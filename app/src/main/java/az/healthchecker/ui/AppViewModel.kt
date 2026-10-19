package az.healthchecker.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import az.healthchecker.backup.*
import az.healthchecker.catalog.CatalogImporter
import az.healthchecker.catalog.ExerciseCatalog
import az.healthchecker.data.*
import az.healthchecker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

const val WATER_ID="usda:173647"
data class Notice(val text:String,val undo:Boolean=false)
/** Dish being composed; the text fields stay raw so half-typed numbers survive until the dish is saved. */
data class DishDraft(val id:String,val name:String="",val ingredients:List<Ingredient> = emptyList(),val cooked:String="",val servings:String="",val icon:String="plate") {
    fun recipe():Recipe = Recipe(ingredients,cooked.takeIf{it.isNotBlank()}?.let(::parseQuantity),servings.takeIf{it.isNotBlank()}?.let{s->s.trim().toIntOrNull()?:throw IllegalArgumentException("Число порций — целое число")},icon)
}

class AppViewModel(application:Application):AndroidViewModel(application) {
    private val context=application.applicationContext
    val repository=HealthRepository(context)
    private val preferences=SettingsRepository(context)
    val snapshot=MutableStateFlow(UserSnapshot())
    val settings=MutableStateFlow(Settings())
    val ready=MutableStateFlow(false)
    val error=MutableStateFlow<String?>(null)
    val notice=MutableStateFlow<Notice?>(null)
    val busy=MutableStateFlow(false)
    val backupBusy=MutableStateFlow(false)
    val backupError=MutableStateFlow<String?>(null)
    val revision=MutableStateFlow(Revision())
    val restorePreview=MutableStateFlow<UserSnapshot?>(null)
    val catalogCount=MutableStateFlow(0)
    @Volatile private var catalog:CatalogImporter?=null
    // The diary opens as soon as the database is read; the bundled catalog is parsed in parallel and only
    // search and quick water wait for it.
    private val catalogLoad=viewModelScope.async(Dispatchers.IO){CatalogImporter.shared(context).also{catalog=it;catalogCount.value=it.foods.size}}
    val exercises=MutableStateFlow<List<Exercise>>(emptyList())
    @Volatile private var exerciseCatalog:ExerciseCatalog?=null
    private val exerciseLoad=viewModelScope.async(Dispatchers.IO){ExerciseCatalog.shared(context).also{exerciseCatalog=it;exercises.value=it.exercises}}
    val dish=MutableStateFlow<DishDraft?>(null)
    private var undo:(suspend ()->kotlin.Unit)?=null
    private val runningBackups=AtomicInteger()
    private val backups=BackupCoordinator(object:BackupEnvironment {
        override suspend fun configuration()=preferences.settings.first().let{BackupConfiguration(it.folder,it.lastBackup,it.backups)}
        override suspend fun selectFolder(folder:String)=preferences.folder(folder)
        override suspend fun snapshot()=repository.exportVersioned()
        override suspend fun savedRevision()=repository.backedUpRevision()
        // Preferences first: the previous file stays listed even if marking the revision fails.
        override suspend fun recordSaved(folder:String,files:List<String>,revision:Long){preferences.saved(files);repository.markBackedUp(revision)}
        override fun store(folder:String)=SafBackupStore(context,Uri.parse(folder))
    })
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try { snapshot.value=repository.exportSnapshot();ready.value=true }
            catch(e:Exception){error.value="Не удалось открыть данные: ${e.message}"}
        }
        viewModelScope.launch { try{catalogLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){error.value="Не удалось открыть каталог продуктов: ${e.message}"} }
        viewModelScope.launch { try{exerciseLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){error.value="Не удалось открыть каталог упражнений: ${e.message}"} }
        viewModelScope.launch(Dispatchers.IO){repository.snapshots.collect{snapshot.value=it}}
        viewModelScope.launch(Dispatchers.IO){preferences.settings.collect{settings.value=it}}
        viewModelScope.launch(Dispatchers.IO){repository.revisions.collect{r->revision.value=r;if(r.current!=r.backedUp)performBackup(false)}}
    }
    suspend fun search(query:String):List<Food> {
        val loaded=try{catalogLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){null}
        return withContext(Dispatchers.Default){loaded?.search(query,snapshot.value.customFoods)?:emptyList()}
    }
    fun food(id:String):Food? = snapshot.value.customFoods.find{it.id==id}?:catalog?.byId(id)
    fun foodFor(entry:DiaryEntry):Food {
        fun base(v:Double?)=v?.times(100/entry.grams)
        val n=entry.nutrients
        return Food(entry.foodId,entry.name,per100g=Nutrients(base(n.kcal),base(n.protein),base(n.fat),base(n.carbs)),portions=entry.quantity.portionGrams?.let{listOf(Portion("Порция записи",it))}?:emptyList(),density=if(entry.quantity.unit==Measure.ML)entry.grams/entry.quantity.value else null,source="Сохранённая запись",isWater=entry.waterMl>0)
    }
    fun action(onSuccess:()->kotlin.Unit = {},block:suspend ()->kotlin.Unit) {
        if(busy.value)return
        busy.value=true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO){block()};onSuccess() }
            catch(e:CancellationException){throw e}
            catch(e:Exception){error.value=e.message?:"Не удалось сохранить изменения"}
            finally{busy.value=false}
        }
    }
    private fun offerUndo(text:String,revert:suspend ()->kotlin.Unit){undo=revert;notice.value=Notice(text,true)}
    fun saveEntry(id:String,food:Food,q:Quantity,date:String,old:DiaryEntry?,done:()->kotlin.Unit) = action(done) {
        repository.upsertEntry(makeEntry(id,food,q,date,old,Instant.now(),ZoneId.systemDefault()))
    }
    fun water(date:String)=action {
        val f=catalogLoad.await().byId(WATER_ID)?:error("Вода не найдена")
        val e=makeEntry(UUID.randomUUID().toString(),f,Quantity(250.0,Measure.ML),date,null,Instant.now(),ZoneId.systemDefault())
        repository.upsertEntry(e);offerUndo("Добавлено 250 мл воды"){repository.deleteEntry(e.id)}
    }
    fun delete(entry:DiaryEntry)=action { repository.deleteEntry(entry.id);offerUndo("Запись удалена"){repository.upsertEntry(entry)} }
    fun undo()=action { undo?.invoke();undo=null;notice.value=null }
    fun dismissNotice(){notice.value=null}
    fun completeDay(date:String,value:Boolean)=action { repository.completeDay(date,value) }
    fun exercise(id:String):Exercise? = exerciseCatalog?.byId(id)
    suspend fun searchExercises(query:String,muscle:Muscle?,kind:ExerciseKind?,dumbbells:Boolean):List<Exercise> {
        val loaded=try{exerciseLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){null}
        return withContext(Dispatchers.Default){loaded?.search(query,muscle,kind,dumbbells)?:emptyList()}
    }
    fun saveWorkout(id:String,exercise:Exercise,sets:Int?,reps:Int?,seconds:Int?,loadKg:Double?,minutes:Double,date:String,old:WorkoutEntry?,done:()->kotlin.Unit)=action(done) {
        repository.upsertWorkout(makeWorkout(id,exercise,sets,reps,seconds,loadKg,minutes,date,old,Instant.now(),ZoneId.systemDefault(),snapshot.value.profile?.currentWeight))
    }
    fun deleteWorkout(workout:WorkoutEntry)=action { repository.deleteWorkout(workout.id);offerUndo("Упражнение удалено"){repository.upsertWorkout(workout)} }
    fun markTraining(date:String,trained:Boolean?)=action { repository.markTraining(date,trained) }
    fun trainingPrefs(prefs:TrainingPrefs)=action { repository.saveTrainingPrefs(prefs) }
    /** Opens a draft for a new dish, or for editing [existing]; the id is kept so diary history stays linked. */
    fun startDish(existing:Food?) { dish.value=existing?.recipe?.let{r->DishDraft(existing.id,existing.name,r.ingredients,r.cookedGrams?.let(::plain)?:"",r.servings?.toString()?:"",r.icon)}?:DishDraft("custom:dish:"+UUID.randomUUID()) }
    fun editDish(change:(DishDraft)->DishDraft) { dish.value=dish.value?.let(change) }
    fun putIngredient(index:Int?,food:Food,quantity:Quantity) { val i=ingredientOf(food,quantity);editDish{d->d.copy(ingredients=if(index==null||index !in d.ingredients.indices)d.ingredients+i else d.ingredients.toMutableList().also{it[index]=i})} }
    fun foodForIngredient(i:Ingredient):Food = food(i.foodId)?:Food(i.foodId,i.name,per100g=i.nutrients.let{n->val k=100/i.grams;Nutrients(n.kcal?.times(k),n.protein?.times(k),n.fat?.times(k),n.carbs?.times(k))},portions=i.quantity.portionGrams?.let{listOf(Portion("Порция ингредиента",it))}?:emptyList(),density=if(i.quantity.unit==Measure.ML)i.grams/i.quantity.value else null,source="Ингредиент блюда")
    /** Deleting a dish keeps every diary entry made from it: entries store their own nutrient snapshot. */
    fun deleteDish(food:Food)=action { repository.deleteFood(food.id);offerUndo("Блюдо «${food.name}» удалено"){repository.saveFood(food)} }
    fun eat(food:Food,quantity:Quantity,date:String,done:()->kotlin.Unit)=action(done) {
        val e=makeEntry(UUID.randomUUID().toString(),food,quantity,date,null,Instant.now(),ZoneId.systemDefault())
        repository.upsertEntry(e);offerUndo("Записано в дневник: ${food.name}, ${number(e.nutrients.kcal)} ккал"){repository.deleteEntry(e.id)}
    }
    fun saveDish(done:(Food)->kotlin.Unit) {
        val d=dish.value?:return
        val food=try{buildDish(d.id,d.name,d.recipe())}catch(e:IllegalArgumentException){error.value=e.message;return}
        action({dish.value=null;done(food)}){repository.saveFood(food)}
    }
    fun saveProfile(profile:Profile?,goals:DailyGoals,done:()->kotlin.Unit)=action(done) { profile?.let{repository.saveProfile(it)};repository.saveGoals(goals);repository.finishOnboarding() }
    fun skipOnboarding()=action { repository.finishOnboarding() }
    fun customFood(food:Food,done:()->kotlin.Unit)=action(done){repository.saveFood(food)}
    fun theme(value:String)=action{preferences.theme(value)}
    fun selectFolder(uri:Uri)=action {
        val flags=Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri,flags)
        val previous=preferences.settings.first().folder
        backups.selectFolder(uri.toString())
        if(previous.isNotEmpty() && previous!=uri.toString())runCatching{context.contentResolver.releasePersistableUriPermission(Uri.parse(previous),flags)}
        // Re-selecting the same folder restores access after a revoked permission, so always write a fresh copy.
        viewModelScope.launch(Dispatchers.IO){performBackup(true)}
    }
    fun backupNow(){viewModelScope.launch(Dispatchers.IO){performBackup(true)}}
    private suspend fun performBackup(force:Boolean) {
        backupBusy.value=runningBackups.incrementAndGet()>0
        try { backups.backup(force);backupError.value=null }
        catch(e:CancellationException){throw e}
        catch(e:Exception){
            val message="Резервная копия не сохранена. ${e.message?:""}".trim()
            backupError.value=message
            // Automatic attempts report in the profile; only an explicit request interrupts with a dialog.
            if(force)error.value=message
        }
        finally{backupBusy.value=runningBackups.decrementAndGet()>0}
    }
    fun previewRestore(uri:Uri)=action {
        val bytes=context.contentResolver.openInputStream(uri)?.use{BackupCodec.readLimited(it)}?:error("Файл недоступен")
        restorePreview.value=BackupCodec.decode(bytes)
    }
    fun confirmRestore()=action {
        val s=restorePreview.value?:return@action
        repository.replaceSnapshot(s);restorePreview.value=null;undo=null
        notice.value=Notice(if(settings.value.folder.isEmpty())"Данные восстановлены. Выберите папку для новых копий в профиле." else "Данные восстановлены")
    }
}

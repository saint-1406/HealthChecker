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
import az.healthchecker.steps.StepTracking
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
    fun recipe():Recipe = Recipe(ingredients,cooked.takeIf{it.isNotBlank()}?.let(::parseQuantity),servings.takeIf{it.isNotBlank()}?.let{s->s.trim().toIntOrNull()?:throw IllegalArgumentException(tr("Число порций — целое число","Servings must be a whole number"))},icon)
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
    /** Exercise id → picture file version, so cards know which exercises have a picture and reload a replaced one. */
    val imageVersions=MutableStateFlow<Map<String,Long>>(emptyMap())
    /** One stopwatch for the whole app: it keeps running while the user moves between the exercise list and a video. */
    val stopwatch=MutableStateFlow(Stopwatch())
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
    val language=MutableStateFlow(Lang.current)
    /** Automatic step counting: switched on, and whether this phone has a step counter at all. */
    val stepTracking=MutableStateFlow(StepTracking.enabled(context))
    val stepSensor=StepTracking.sensorAvailable(context)
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
        viewModelScope.launch(Dispatchers.IO){imageVersions.value=repository.imageVersions()}
        viewModelScope.launch(Dispatchers.IO) {
            try { snapshot.value=repository.exportSnapshot();ready.value=true }
            catch(e:Exception){error.value=tr("Не удалось открыть данные: ","Could not open your data: ")+e.message}
        }
        viewModelScope.launch {
            try{val c=catalogLoad.await();withContext(Dispatchers.IO){repository.backfillFiber{id->c.byId(id)}}}
            catch(e:CancellationException){throw e}catch(e:Exception){error.value=tr("Не удалось открыть каталог продуктов: ","Could not open the food catalog: ")+e.message}
        }
        viewModelScope.launch { try{exerciseLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){error.value=tr("Не удалось открыть каталог упражнений: ","Could not open the exercise catalog: ")+e.message} }
        viewModelScope.launch(Dispatchers.IO){repository.snapshots.collect{snapshot.value=it}}
        viewModelScope.launch(Dispatchers.IO){preferences.settings.collect{settings.value=it}}
        viewModelScope.launch(Dispatchers.IO){repository.revisions.collect{r->revision.value=r;if(r.current!=r.backedUp)performBackup(false)}}
        // Counting may have been stopped by the system; start it again if the user left it on.
        if(stepTracking.value)StepTracking.resume(context)
    }
    fun setLanguage(value:AppLanguage){LanguageStore.save(context,value);language.value=value}
    /** The name of a logged food in the current language; the user's own foods and removed products keep the saved name. */
    fun entryName(e:DiaryEntry):String = catalog?.byId(e.foodId)?.displayName?:e.name
    fun workoutName(w:WorkoutEntry):String = exerciseCatalog?.byId(w.exerciseId)?.displayName?:w.name
    suspend fun search(query:String):List<Food> {
        val loaded=try{catalogLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){null}
        return withContext(Dispatchers.Default){loaded?.search(query,snapshot.value.customFoods)?:emptyList()}
    }
    fun food(id:String):Food? = snapshot.value.customFoods.find{it.id==id}?:catalog?.byId(id)
    fun foodFor(entry:DiaryEntry):Food {
        val catalogFood=catalog?.byId(entry.foodId)
        return Food(entry.foodId,entry.name,per100g=entry.nutrients.times(100/entry.grams),portions=entry.quantity.portionGrams?.let{listOf(Portion("Порция записи",it,"Logged portion"))}?:emptyList(),density=if(entry.quantity.unit==Measure.ML)entry.grams/entry.quantity.value else null,source="Сохранённая запись",isWater=entry.waterMl>0,
            nameEn=catalogFood?.nameEn,sourceEn="Saved entry")
    }
    fun action(onSuccess:()->kotlin.Unit = {},block:suspend ()->kotlin.Unit) {
        if(busy.value)return
        busy.value=true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO){block()};onSuccess() }
            catch(e:CancellationException){throw e}
            catch(e:Exception){error.value=e.message?:tr("Не удалось сохранить изменения","Could not save the changes")}
            finally{busy.value=false}
        }
    }
    private fun offerUndo(text:String,revert:suspend ()->kotlin.Unit){undo=revert;notice.value=Notice(text,true)}
    fun saveEntry(id:String,food:Food,q:Quantity,date:String,old:DiaryEntry?,done:()->kotlin.Unit) = action(done) {
        repository.upsertEntry(makeEntry(id,food,q,date,old,Instant.now(),ZoneId.systemDefault()))
    }
    fun water(date:String)=action {
        val f=catalogLoad.await().byId(WATER_ID)?:error(tr("Вода не найдена","Water not found"))
        val e=makeEntry(UUID.randomUUID().toString(),f,Quantity(250.0,Measure.ML),date,null,Instant.now(),ZoneId.systemDefault())
        repository.upsertEntry(e);offerUndo(tr("Добавлено 250 мл воды","Added 250 ml of water")){repository.deleteEntry(e.id)}
    }
    fun delete(entry:DiaryEntry)=action { repository.deleteEntry(entry.id);offerUndo(tr("Запись удалена","Entry deleted")){repository.upsertEntry(entry)} }
    fun undo()=action { undo?.invoke();undo=null;notice.value=null }
    fun dismissNotice(){notice.value=null}
    fun completeDay(date:String,value:Boolean)=action { repository.completeDay(date,value) }
    fun exercise(id:String):Exercise? = exerciseCatalog?.byId(id)
    suspend fun searchExercises(query:String,muscle:Muscle?,category:ExerciseCategory?,dumbbells:Boolean):List<Exercise> {
        val loaded=try{exerciseLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){null}
        return withContext(Dispatchers.Default){loaded?.search(query,muscle,category,dumbbells)?:emptyList()}
    }
    fun saveWorkout(id:String,exercise:Exercise,sets:Int?,reps:Int?,seconds:Int?,loadKg:Double?,minutes:Double,date:String,old:WorkoutEntry?,done:()->kotlin.Unit)=action(done) {
        repository.upsertWorkout(makeWorkout(id,exercise,sets,reps,seconds,loadKg,minutes,date,old,Instant.now(),ZoneId.systemDefault(),snapshot.value.bodyWeight(date)))
    }
    fun imageFile(id:String):java.io.File = repository.imageFile(id)
    fun setExerciseImage(id:String,uri:Uri)=action {
        repository.saveExerciseImage(id,encodeExerciseImage(context,uri));imageVersions.value=repository.imageVersions()
    }
    fun removeExerciseImage(id:String)=action {
        val old=repository.imageFile(id).takeIf{it.isFile}?.readBytes()
        repository.deleteExerciseImage(id);imageVersions.value=repository.imageVersions()
        old?.let{bytes->offerUndo(tr("Картинка удалена","Picture removed")){repository.saveExerciseImage(id,bytes);imageVersions.value=repository.imageVersions()}}
    }
    private fun clock()=android.os.SystemClock.elapsedRealtime()
    fun startStopwatch(){stopwatch.value=stopwatch.value.start(clock())}
    fun pauseStopwatch(){stopwatch.value=stopwatch.value.pause(clock())}
    fun resetStopwatch(){stopwatch.value=stopwatch.value.reset()}
    fun stopwatchMs():Long = stopwatch.value.elapsed(clock())
    fun saveWeight(date:String,kg:Double,done:()->kotlin.Unit)=action(done) { repository.saveWeight(makeWeight(date,kg,Instant.now(),ZoneId.systemDefault())) }
    fun deleteWeight(entry:WeightEntry)=action { repository.deleteWeight(entry.date);offerUndo(tr("Вес удалён","Weight deleted")){repository.saveWeight(entry)} }
    fun deleteWorkout(workout:WorkoutEntry)=action { repository.deleteWorkout(workout.id);offerUndo(tr("Упражнение удалено","Exercise deleted")){repository.upsertWorkout(workout)} }
    fun markTraining(date:String,trained:Boolean?)=action { repository.markTraining(date,trained) }
    fun trainingPrefs(prefs:TrainingPrefs)=action { repository.saveTrainingPrefs(prefs) }
    /** Opens a draft for a new dish, or for editing [existing]; the id is kept so diary history stays linked. */
    fun startDish(existing:Food?) { dish.value=existing?.recipe?.let{r->DishDraft(existing.id,existing.name,r.ingredients,r.cookedGrams?.let(::plain)?:"",r.servings?.toString()?:"",r.icon)}?:DishDraft("custom:dish:"+UUID.randomUUID()) }
    fun editDish(change:(DishDraft)->DishDraft) { dish.value=dish.value?.let(change) }
    fun putIngredient(index:Int?,food:Food,quantity:Quantity) { val i=ingredientOf(food,quantity);editDish{d->d.copy(ingredients=if(index==null||index !in d.ingredients.indices)d.ingredients+i else d.ingredients.toMutableList().also{it[index]=i})} }
    fun foodForIngredient(i:Ingredient):Food = food(i.foodId)?:Food(i.foodId,i.name,per100g=i.nutrients.times(100/i.grams),portions=i.quantity.portionGrams?.let{listOf(Portion("Порция ингредиента",it,"Ingredient portion"))}?:emptyList(),density=if(i.quantity.unit==Measure.ML)i.grams/i.quantity.value else null,source="Ингредиент блюда",sourceEn="Dish ingredient")
    /** Deleting a dish keeps every diary entry made from it: entries store their own nutrient snapshot. */
    fun deleteDish(food:Food)=action { repository.deleteFood(food.id);offerUndo(tr("Блюдо «${food.name}» удалено","Dish “${food.name}” deleted")){repository.saveFood(food)} }
    fun eat(food:Food,quantity:Quantity,date:String,done:()->kotlin.Unit)=action(done) {
        val e=makeEntry(UUID.randomUUID().toString(),food,quantity,date,null,Instant.now(),ZoneId.systemDefault())
        repository.upsertEntry(e);offerUndo(tr("Записано в дневник: ","Logged: ")+"${food.displayName}, ${number(e.nutrients.kcal)} $KCAL"){repository.deleteEntry(e.id)}
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
            val message=(tr("Резервная копия не сохранена. ","Backup not saved. ")+(e.message?:"")).trim()
            backupError.value=message
            // Automatic attempts report in the profile; only an explicit request interrupts with a dialog.
            if(force)error.value=message
        }
        finally{backupBusy.value=runningBackups.decrementAndGet()>0}
    }
    fun previewRestore(uri:Uri)=action {
        val bytes=context.contentResolver.openInputStream(uri)?.use{BackupCodec.readLimited(it)}?:error(tr("Файл недоступен","The file is unavailable"))
        restorePreview.value=BackupCodec.decode(bytes)
    }
    fun confirmRestore()=action {
        val s=restorePreview.value?:return@action
        repository.replaceSnapshot(s);restorePreview.value=null;undo=null;imageVersions.value=repository.imageVersions()
        notice.value=Notice(if(settings.value.folder.isEmpty())tr("Данные восстановлены. Выберите папку для новых копий в профиле.","Data restored. Choose a folder for new backups in the profile.") else tr("Данные восстановлены","Data restored"))
    }
    fun saveMedication(medication:Medication,done:()->kotlin.Unit)=action(done){repository.saveMedication(medication)}
    fun deleteMedication(medication:Medication)=action { repository.deleteMedication(medication.id);offerUndo(tr("Препарат «${medication.name}» удалён","“${medication.name}” deleted")){repository.saveMedication(medication)} }
    fun takeMedication(medication:Medication,date:String)=action {
        val t=makeIntake(medication,date,Instant.now(),ZoneId.systemDefault())
        repository.upsertIntake(t);offerUndo(tr("Отмечен приём: ","Dose taken: ")+medication.name){repository.deleteIntake(t.id)}
    }
    fun deleteIntake(intake:MedicationIntake)=action { repository.deleteIntake(intake.id);offerUndo(tr("Отметка приёма удалена","Dose mark removed")){repository.upsertIntake(intake)} }
    fun setManualSteps(date:String,steps:Int?,done:()->kotlin.Unit={})=action(done){repository.setManualSteps(date,steps)}
    fun setStepTracking(value:Boolean){StepTracking.setEnabled(context,value);stepTracking.value=value}
    /** Android may have refused to start counting (permission withdrawn); the switch shows what really runs. */
    fun refreshStepTracking(){stepTracking.value=StepTracking.enabled(context)}
}

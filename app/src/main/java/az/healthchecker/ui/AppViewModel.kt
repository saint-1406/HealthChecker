package az.healthchecker.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import az.healthchecker.backup.*
import az.healthchecker.catalog.CatalogImporter
import az.healthchecker.catalog.ExerciseCatalog
import az.healthchecker.catalog.FigureMotion
import az.healthchecker.catalog.VideoLibrary
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
    /** Photos for later, newest first. */
    val photos=MutableStateFlow<List<InboxPhoto>>(emptyList())
    private val inbox=PhotoInbox(context)
    /** One stopwatch for the whole app: it keeps running while the user moves between the exercise list and a video. */
    val stopwatch=MutableStateFlow(Stopwatch())
    /** Rest or hold timer of the workout screen: a short beep for each of the last three seconds, then a long one and vibration. */
    val countdown=MutableStateFlow<Countdown?>(null)
    private var countdownWatch:Job?=null
    private val signal by lazy{TimerSignal(context)}
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
    /** Built-in workout videos with their exercise timelines; the user's own videos are in the snapshot. */
    val libraryVideos=MutableStateFlow<List<WorkoutVideo>>(emptyList())
    /** Where each video was left in this session, so coming back (or turning the phone) continues from there. */
    val videoPositions=MutableStateFlow<Map<String,Int>>(emptyMap())
    /** Following a video by its timeline while it plays elsewhere (the YouTube app, a TV): a stopwatch for one video. */
    val videoClock=MutableStateFlow<Pair<String,Stopwatch>?>(null)
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
        viewModelScope.launch(Dispatchers.IO){imageVersions.value=repository.imageVersions();photos.value=inbox.list()}
        viewModelScope.launch(Dispatchers.IO) {
            try { snapshot.value=repository.exportSnapshot();ready.value=true }
            catch(e:Exception){error.value=tr("Не удалось открыть данные: ","Could not open your data: ")+e.message}
        }
        viewModelScope.launch {
            try{val c=catalogLoad.await();withContext(Dispatchers.IO){repository.backfillFiber{id->c.byId(id)}}}
            catch(e:CancellationException){throw e}catch(e:Exception){error.value=tr("Не удалось открыть каталог продуктов: ","Could not open the food catalog: ")+e.message}
        }
        viewModelScope.launch { try{exerciseLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){error.value=tr("Не удалось открыть каталог упражнений: ","Could not open the exercise catalog: ")+e.message} }
        viewModelScope.launch(Dispatchers.IO){libraryVideos.value=VideoLibrary.shared(context).videos}
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
    /** Foods for the protein list: the user's own and everyday catalog foods, or the whole catalog; a name narrows them. */
    suspend fun findProteinFoods(query:String,grams:Double?,match:ProteinMatch,sort:ProteinSort,wholeCatalog:Boolean):List<Food> {
        val loaded=try{catalogLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){null}
        val custom=snapshot.value.customFoods;val own=custom.map{it.id}.toSet()
        return withContext(Dispatchers.Default){
            val pool=if(query.isBlank())custom+(loaded?.foods?.filter{wholeCatalog||it.common}?:emptyList())
                else (loaded?.search(query,custom,1000)?:custom).filter{wholeCatalog||it.common||it.id in own}
            proteinFoods(pool,grams,match,sort)
        }
    }
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
    fun figure(id:String):FigureMotion? = exerciseCatalog?.figure(id)
    suspend fun searchExercises(query:String,muscle:Muscle?,category:ExerciseCategory?,dumbbells:Boolean):List<Exercise> {
        val loaded=try{exerciseLoad.await()}catch(e:CancellationException){throw e}catch(e:Exception){null}
        return withContext(Dispatchers.Default){loaded?.search(query,muscle,category,dumbbells)?:emptyList()}
    }
    fun saveWorkout(id:String,exercise:Exercise,sets:Int?,reps:Int?,seconds:Int?,loadKg:Double?,minutes:Double,date:String,old:WorkoutEntry?,done:()->kotlin.Unit)=action(done) {
        repository.upsertWorkout(makeWorkout(id,exercise,sets,reps,seconds,loadKg,minutes,date,old,Instant.now(),ZoneId.systemDefault(),snapshot.value.bodyWeight(date)))
    }
    /** Logs a suggested exercise exactly as prescribed, straight from the workout screen; undo removes it again. */
    fun logPrescribed(exercise:Exercise,p:Prescription,date:String)=action {
        val minutes=p.minutesFor(exercise)?:error(tr("Укажите время упражнения","Enter the exercise time"))
        val w=makeWorkout(UUID.randomUUID().toString(),exercise,p.sets,p.reps,p.seconds,null,minutes,date,null,Instant.now(),ZoneId.systemDefault(),snapshot.value.bodyWeight(date))
        repository.upsertWorkout(w)
        offerUndo(tr("Записано: ","Logged: ")+exercise.displayName){repository.deleteWorkout(w.id)}
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
    fun photoFile(photo:InboxPhoto):java.io.File = inbox.file(photo)
    /** The file the camera app writes the next shot into; it is the same path every time, so it survives the app being stopped meanwhile. */
    fun cameraTarget():Uri {
        val file=java.io.File(context.cacheDir,"camera/shot.jpg");file.parentFile?.mkdirs()
        return androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.photos",file)
    }
    /** Keeps a photo from the camera or the gallery for later; a camera shot's temporary file is removed afterwards. */
    fun keepPhoto(uri:Uri,kind:PhotoKind)=action {
        try{inbox.add(kind,System.currentTimeMillis(),encodeImage(context,uri,PHOTO_MAX_PX,88))}
        finally{if(uri.authority=="${context.packageName}.photos")java.io.File(context.cacheDir,"camera/shot.jpg").delete()}
        photos.value=inbox.list()
        notice.value=Notice(tr("Фото сохранено. Внесите его, когда будет время: «Дневник» → «Фото на потом»","Photo kept. Log it when you have time: Diary → Photos for later"))
    }
    fun setPhotoKind(photo:InboxPhoto,kind:PhotoKind,done:(InboxPhoto)->kotlin.Unit)=action {
        val moved=inbox.setKind(photo,kind);photos.value=inbox.list();withContext(Dispatchers.Main){done(moved)}
    }
    fun deletePhoto(photo:InboxPhoto,done:()->kotlin.Unit={})=action(done) {
        val bytes=inbox.read(photo);inbox.delete(photo);photos.value=inbox.list()
        bytes?.let{b->offerUndo(tr("Фото удалено","Photo deleted")){inbox.write(photo,b);photos.value=inbox.list()}}
    }
    private fun clock()=android.os.SystemClock.elapsedRealtime()
    fun startStopwatch(){stopwatch.value=stopwatch.value.start(clock())}
    fun pauseStopwatch(){stopwatch.value=stopwatch.value.pause(clock())}
    fun resetStopwatch(){stopwatch.value=stopwatch.value.reset()}
    fun stopwatchMs():Long = stopwatch.value.elapsed(clock())
    fun startCountdown(seconds:Int,label:String){countdown.value=Countdown.start(clock(),seconds*1000L,label);watchCountdown()}
    fun pauseCountdown(){countdownWatch?.cancel();countdown.value=countdown.value?.pause(clock())}
    fun resumeCountdown(){countdown.value=countdown.value?.resume(clock());watchCountdown()}
    fun addCountdown(seconds:Int){countdown.value=countdown.value?.plus(clock(),seconds*1000L);watchCountdown()}
    fun cancelCountdown(){countdownWatch?.cancel();countdown.value=null}
    fun countdownMs():Long = countdown.value?.remaining(clock())?:0
    // The signal comes from here rather than from the screen, so it sounds while the user is looking at another one.
    private fun watchCountdown() {
        countdownWatch?.cancel()
        if(countdown.value?.running!=true)return
        countdownWatch=viewModelScope.launch {
            var ticked=minOf(4L,(countdownMs()+999)/1000+1)
            while(true){
                val c=countdown.value?:break
                val left=c.remaining(clock())
                if(left==0L){countdown.value=c.pause(clock());signal.done();break}
                val s=(left+999)/1000
                if(s<=3&&s<ticked){ticked=s;signal.tick()}
                delay(minOf(left,50))
            }
        }
    }
    fun video(id:String):WorkoutVideo? = libraryVideos.value.find{it.id==id}?:snapshot.value.videos.find{it.id==id}
    fun videoPosition(id:String,second:Int){if(videoPositions.value[id]!=second)videoPositions.value=videoPositions.value+(id to second)}
    fun videoClockMs(id:String):Long = videoClock.value?.takeIf{it.first==id}?.second?.elapsed(clock())?:(videoPositions.value[id]?:0)*1000L
    fun startVideoClock(id:String){val c=videoClock.value;videoClock.value=id to (if(c?.first==id)c.second else Stopwatch((videoPositions.value[id]?:0)*1000L)).start(clock())}
    fun pauseVideoClock(){videoClock.value=videoClock.value?.let{(id,w)->id to w.pause(clock())}}
    fun seekVideoClock(id:String,second:Int){val c=videoClock.value;videoClock.value=id to (if(c?.first==id)c.second else Stopwatch()).seek(clock(),second*1000L);videoPosition(id,second)}
    /** Whether this install may still read the user's file: the link dies with a reinstall or when the file is gone. */
    fun videoFileReadable(file:VideoFile):Boolean = context.contentResolver.persistedUriPermissions.any{it.uri.toString()==file.uri&&it.isReadPermission}
    fun addVideo(title:String,link:String,done:(WorkoutVideo)->kotlin.Unit)=action {
        val v=makeOwnVideo("video:"+UUID.randomUUID(),title,link,Instant.now())
        repository.saveVideo(v);withContext(Dispatchers.Main){done(v)}
    }
    /**
     * Links the user's own video file (picked with the system file picker) to a video, or adds it as a new video named after
     * the file. Only the link is kept: the file stays where it is and plays without the internet.
     */
    fun attachVideoFile(uri:Uri,videoId:String?,done:(String)->kotlin.Unit={})=action {
        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else null}?:tr("Видео","Video")
        val id=videoId?:("video:"+UUID.randomUUID()).also{id->repository.saveVideo(makeOwnVideo(id,name.substringBeforeLast('.').take(200),"",Instant.now()))}
        releaseVideoFile(preferences.videoFile(id,VideoFile(uri.toString(),name)))
        withContext(Dispatchers.Main){done(id)}
    }
    fun removeVideoFile(id:String)=action { releaseVideoFile(preferences.videoFile(id,null)) }
    /** Gives the file access back to Android once no video links that file any more. */
    private suspend fun releaseVideoFile(old:VideoFile?) {
        if(old==null||preferences.settings.first().videoFiles.values.any{it.uri==old.uri})return
        runCatching{context.contentResolver.releasePersistableUriPermission(old.uri.toUri(),Intent.FLAG_GRANT_READ_URI_PERMISSION)}
    }
    fun deleteVideo(video:WorkoutVideo,done:()->kotlin.Unit={})=action(done) {
        val file=preferences.settings.first().videoFiles[video.id]
        repository.deleteVideo(video.id)
        offerUndo(tr("Видео «${video.title}» удалено","Video “${video.title}” deleted")){repository.saveVideo(video)}
        // The file link goes with the video; undo brings the video back without it.
        file?.let{releaseVideoFile(preferences.videoFile(video.id,null))}
    }
    /** Logs the exercises of a video workout, the whole video or up to [upTo] seconds, as one change that undo takes back. */
    fun logVideo(video:WorkoutVideo,upTo:Int?,date:String)=action {
        val weight=snapshot.value.bodyWeight(date);val now=Instant.now();val zone=ZoneId.systemDefault()
        val entries=video.workDone(upTo).mapNotNull{w->exercise(w.exerciseId)?.let{videoWorkout(UUID.randomUUID().toString(),it,w,date,now,zone,weight)}}
        require(entries.isNotEmpty()){tr("В этом отрезке видео ещё нет упражнений","This part of the video has no exercises yet")}
        repository.upsertWorkouts(entries)
        offerUndo(tr("Записано из видео: ","Logged from the video: ")+count(entries.size,"упражнение","упражнения","упражнений","exercise","exercises")){repository.deleteWorkouts(entries.map{it.id})}
    }
    /** A video without a timeline goes to the diary as one video workout of the Compendium's class. */
    fun logVideoAsWorkout(minutes:Double,vigorous:Boolean,date:String)=action {
        val e=exercise(if(vigorous)"video_cardio_hard" else "video_cardio")?:error(tr("Упражнение не найдено","Exercise not found"))
        val w=makeWorkout(UUID.randomUUID().toString(),e,null,null,null,null,minutes,date,null,Instant.now(),ZoneId.systemDefault(),snapshot.value.bodyWeight(date))
        repository.upsertWorkout(w);offerUndo(tr("Записано: ","Logged: ")+e.displayName){repository.deleteWorkout(w.id)}
    }
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
    /** Changes the look and saves it; the screen updates at once, without waiting for the settings file. */
    fun appearance(change:(Appearance)->Appearance) {
        val next=change(settings.value.appearance);settings.value=settings.value.copy(appearance=next)
        viewModelScope.launch{preferences.appearance(next)}
    }
    fun backgroundFile():java.io.File = java.io.File(context.filesDir,"appearance/background.jpg")
    /** The user's own background picture, kept in the app's private files like exercise pictures. */
    fun setBackground(uri:Uri)=action {
        val bytes=encodeImage(context,uri,BACKGROUND_MAX_PX,85)
        val file=backgroundFile();file.parentFile?.mkdirs();val tmp=java.io.File(file.path+".tmp")
        tmp.writeBytes(bytes);if(!tmp.renameTo(file)){file.delete();check(tmp.renameTo(file)){tr("Не удалось сохранить фон","Could not save the background")}}
        preferences.appearance(settings.value.appearance.copy(background=System.currentTimeMillis()))
    }
    fun removeBackground()=action { backgroundFile().delete();preferences.appearance(settings.value.appearance.copy(background=0)) }
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

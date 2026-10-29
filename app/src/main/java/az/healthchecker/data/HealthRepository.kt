package az.healthchecker.data
import android.content.Context
import az.healthchecker.domain.*
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.LocalDate
class HealthRepository(context: Context, name: String = "healthchecker.db") {
    // The step counter service writes through its own instance; invalidation keeps the screens up to date.
    private val db=Room.databaseBuilder(context.applicationContext,HealthDatabase::class.java,name).enableMultiInstanceInvalidation().build()
    private val dao=db.dao()
    private val json=Json{encodeDefaults=true}
    private val images=ExerciseImageStore(context)
    val snapshots:Flow<UserSnapshot> = dao.observe().map(::decode)
    val revisions:Flow<Revision> = dao.observeRevision().map{it?:Revision()}
    private fun decode(rows:List<StoredRecord>):UserSnapshot {
        // One grouping pass instead of rescanning the entire diary for every record type.
        val kinds=rows.groupBy{it.kind}
        fun records(kind:String)=kinds[kind].orEmpty()
        return UserSnapshot(
        profile=records("profile").firstOrNull()?.let{json.decodeFromString<Profile>(it.payload)},
        goals=records("goal").map{json.decodeFromString<DailyGoals>(it.payload)},
        entries=records("entry").map{json.decodeFromString<DiaryEntry>(it.payload)},
        customFoods=records("food").map{json.decodeFromString<Food>(it.payload)},
        completedDays=records("day").map{it.payload}.toSet(),onboarded=records("onboarded").isNotEmpty(),
        workouts=records("workout").map{json.decodeFromString<WorkoutEntry>(it.payload)},
        trainingDays=records("trainday").associate{it.key.removePrefix("trainday:") to (it.payload=="true")},
        trainingPrefs=records("trainprefs").firstOrNull()?.let{json.decodeFromString<TrainingPrefs>(it.payload)},
        medications=records("med").map{json.decodeFromString<Medication>(it.payload)},
        medicationIntakes=records("medtake").map{json.decodeFromString<MedicationIntake>(it.payload)},
        steps=records("steps").map{json.decodeFromString<StepDay>(it.payload)},
        weights=records("weight").map{json.decodeFromString<WeightEntry>(it.payload)},
        videos=records("video").map{json.decodeFromString<WorkoutVideo>(it.payload)}.sortedBy{it.addedAt})
    }
    private suspend fun changed() { val r=dao.revision()?:Revision();dao.putRevision(r.copy(current=r.current+1)) }
    suspend fun exportSnapshot():UserSnapshot = db.withTransaction{decode(dao.all())}
    /** For backups: the data plus the exercise pictures, which live as files rather than in the database. */
    suspend fun exportVersioned():Pair<Long,UserSnapshot> {
        val (revision,snapshot)=db.withTransaction{Pair((dao.revision()?:Revision()).current,decode(dao.all()))}
        val encoder=java.util.Base64.getEncoder()
        return revision to snapshot.copy(exerciseImages=images.all().mapValues{encoder.encodeToString(it.value)})
    }
    fun imageVersions():Map<String,Long> = images.versions()
    fun imageFile(id:String):java.io.File = images.file(id)
    suspend fun saveExerciseImage(id:String,jpeg:ByteArray) { images.write(id,jpeg);db.withTransaction{changed()} }
    suspend fun deleteExerciseImage(id:String) { images.delete(id);db.withTransaction{changed()} }
    /** A newer weigh-in also becomes the profile's current weight; the initial weight never changes. */
    suspend fun saveWeight(entry:WeightEntry) { validateSnapshot(UserSnapshot(weights=listOf(entry)));db.withTransaction{
        dao.put(StoredRecord("weight:${entry.date}","weight",json.encodeToString(entry)))
        val newest=dao.all().filter{it.kind=="weight"}.maxOf{it.key.removePrefix("weight:")}==entry.date
        val profile=dao.get("profile")?.let{json.decodeFromString<Profile>(it.payload)}
        if(newest&&profile!=null)dao.put(StoredRecord("profile","profile",json.encodeToString(profile.copy(currentWeight=entry.kg))))
        changed()
    } }
    suspend fun deleteWeight(date:String) { db.withTransaction{
        val deleted=dao.get("weight:$date")?.let{json.decodeFromString<WeightEntry>(it.payload)}
        val weights=dao.all().filter{it.kind=="weight"}.map{json.decodeFromString<WeightEntry>(it.payload)}
        dao.delete("weight:$date")
        val profile=dao.get("profile")?.let{json.decodeFromString<Profile>(it.payload)}
        // Undo the profile update made by the latest weigh-in, preserving a weight edited manually afterwards.
        if(deleted!=null && profile!=null && weights.maxOfOrNull{it.date}==date && profile.currentWeight==deleted.kg) {
            val previous=weights.filter{it.date!=date}.maxByOrNull{it.date}?.kg?:profile.initialWeight
            dao.put(StoredRecord("profile","profile",json.encodeToString(profile.copy(currentWeight=previous))))
        }
        changed()
    } }
    suspend fun backedUpRevision():Long = (dao.revision()?:Revision()).backedUp
    suspend fun markBackedUp(version:Long) { db.withTransaction{val r=dao.revision()?:Revision();dao.putRevision(r.copy(backedUp=version))} }
    suspend fun upsertEntry(entry:DiaryEntry) { validateSnapshot(UserSnapshot(entries=listOf(entry)));db.withTransaction{dao.put(StoredRecord("entry:${entry.id}","entry",json.encodeToString(entry)));changed()} }
    suspend fun deleteEntry(id:String) { db.withTransaction{dao.delete("entry:$id");changed()} }
    suspend fun saveProfile(profile:Profile) { validateSnapshot(UserSnapshot(profile=profile));db.withTransaction{
        val old=dao.get("profile")?.let{json.decodeFromString<Profile>(it.payload)}
        dao.put(StoredRecord("profile","profile",json.encodeToString(profile.copy(initialWeight=old?.initialWeight?:profile.initialWeight))));changed()
    } }
    suspend fun saveGoals(goals:DailyGoals) { validateSnapshot(UserSnapshot(goals=listOf(goals)));db.withTransaction{dao.put(StoredRecord("goal:${goals.effectiveFrom}","goal",json.encodeToString(goals)));changed()} }
    suspend fun saveFood(food:Food) { validateSnapshot(UserSnapshot(customFoods=listOf(food)));db.withTransaction{dao.put(StoredRecord("food:${food.id}","food",json.encodeToString(food)));changed()} }
    suspend fun deleteFood(id:String) { db.withTransaction{dao.delete("food:$id");changed()} }
    suspend fun completeDay(date:String,completed:Boolean) { LocalDate.parse(date);db.withTransaction{if(completed)dao.put(StoredRecord("day:$date","day",date))else dao.delete("day:$date");changed()} }
    suspend fun upsertWorkout(workout:WorkoutEntry) { validateSnapshot(UserSnapshot(workouts=listOf(workout)));db.withTransaction{dao.put(StoredRecord("workout:${workout.id}","workout",json.encodeToString(workout)));changed()} }
    suspend fun deleteWorkout(id:String) { db.withTransaction{dao.delete("workout:$id");changed()} }
    /** A whole video workout in one change, so it reaches the backup (and the undo) together. */
    suspend fun upsertWorkouts(workouts:List<WorkoutEntry>) { validateSnapshot(UserSnapshot(workouts=workouts));db.withTransaction{workouts.forEach{dao.put(StoredRecord("workout:${it.id}","workout",json.encodeToString(it)))};changed()} }
    suspend fun deleteWorkouts(ids:List<String>) { db.withTransaction{ids.forEach{dao.delete("workout:$it")};changed()} }
    suspend fun saveVideo(video:WorkoutVideo) { validateSnapshot(UserSnapshot(videos=listOf(video)));db.withTransaction{dao.put(StoredRecord("video:${video.id}","video",json.encodeToString(video)));changed()} }
    suspend fun deleteVideo(id:String) { db.withTransaction{dao.delete("video:$id");changed()} }
    /** true — a training happened, false — an explicit rest day, null — not marked. */
    suspend fun markTraining(date:String,trained:Boolean?) { LocalDate.parse(date);db.withTransaction{if(trained==null)dao.delete("trainday:$date") else dao.put(StoredRecord("trainday:$date","trainday",trained.toString()));changed()} }
    suspend fun saveTrainingPrefs(prefs:TrainingPrefs) { validateSnapshot(UserSnapshot(trainingPrefs=prefs));db.withTransaction{dao.put(StoredRecord("trainprefs","trainprefs",json.encodeToString(prefs)));changed()} }
    suspend fun saveMedication(medication:Medication) { validateSnapshot(UserSnapshot(medications=listOf(medication)));db.withTransaction{dao.put(StoredRecord("med:${medication.id}","med",json.encodeToString(medication)));changed()} }
    suspend fun deleteMedication(id:String) { db.withTransaction{dao.delete("med:$id");changed()} }
    suspend fun upsertIntake(intake:MedicationIntake) { validateSnapshot(UserSnapshot(medicationIntakes=listOf(intake)));db.withTransaction{dao.put(StoredRecord("medtake:${intake.id}","medtake",json.encodeToString(intake)));changed()} }
    suspend fun deleteIntake(id:String) { db.withTransaction{dao.delete("medtake:$id");changed()} }
    /** The user's own number for a day; null returns the day to the phone's count. */
    suspend fun setManualSteps(date:String,steps:Int?) { db.withTransaction{
        val day=stepDay(date).copy(manual=steps);validateSteps(day);dao.put(StoredRecord("steps:$date","steps",json.encodeToString(day)));changed()
    } }
    /**
     * Adds counted steps. The counter reports many times a day, so only the first write of a day asks for a backup;
     * later counts reach the backup together with the next change.
     */
    suspend fun addSensorSteps(date:String,delta:Int,backup:Boolean):StepDay = db.withTransaction{
        val old=stepDay(date);if(delta<=0&&!backup)return@withTransaction old
        val day=old.copy(sensor=(old.sensor+delta).coerceAtMost(MAX_STEPS_PER_DAY))
        validateSteps(day);dao.put(StoredRecord("steps:$date","steps",json.encodeToString(day)));if(backup)changed()
        day
    }
    private suspend fun stepDay(date:String):StepDay = dao.get("steps:$date")?.let{json.decodeFromString<StepDay>(it.payload)}?:StepDay(LocalDate.parse(date).toString())
    /** Fills fibre into entries logged before it was tracked; returns how many changed. */
    suspend fun backfillFiber(food:(String)->Food?):Int = db.withTransaction {
        var updated=0
        decode(dao.all()).entries.forEach{e->val filled=e.withFiberFrom(food(e.foodId));if(filled!==e){dao.put(StoredRecord("entry:${e.id}","entry",json.encodeToString(filled)));updated++}}
        if(updated>0)changed()
        updated
    }
    suspend fun finishOnboarding(){db.withTransaction{dao.put(StoredRecord("onboarded","onboarded","true"));changed()}}
    suspend fun replaceSnapshot(snapshot:UserSnapshot) {
        validateSnapshot(snapshot)
        db.withTransaction {
            dao.clear()
            snapshot.profile?.let{dao.put(StoredRecord("profile","profile",json.encodeToString(it)))}
            snapshot.goals.forEach{dao.put(StoredRecord("goal:${it.effectiveFrom}","goal",json.encodeToString(it)))}
            snapshot.entries.forEach{dao.put(StoredRecord("entry:${it.id}","entry",json.encodeToString(it)))}
            snapshot.customFoods.forEach{dao.put(StoredRecord("food:${it.id}","food",json.encodeToString(it)))}
            snapshot.completedDays.forEach{dao.put(StoredRecord("day:$it","day",it))}
            if(snapshot.onboarded)dao.put(StoredRecord("onboarded","onboarded","true"))
            snapshot.workouts.forEach{dao.put(StoredRecord("workout:${it.id}","workout",json.encodeToString(it)))}
            snapshot.trainingDays.forEach{(d,v)->dao.put(StoredRecord("trainday:$d","trainday",v.toString()))}
            snapshot.trainingPrefs?.let{dao.put(StoredRecord("trainprefs","trainprefs",json.encodeToString(it)))}
            snapshot.medications.forEach{dao.put(StoredRecord("med:${it.id}","med",json.encodeToString(it)))}
            snapshot.medicationIntakes.forEach{dao.put(StoredRecord("medtake:${it.id}","medtake",json.encodeToString(it)))}
            snapshot.steps.forEach{dao.put(StoredRecord("steps:${it.date}","steps",json.encodeToString(it)))}
            snapshot.weights.forEach{dao.put(StoredRecord("weight:${it.date}","weight",json.encodeToString(it)))}
            snapshot.videos.forEach{dao.put(StoredRecord("video:${it.id}","video",json.encodeToString(it)))}
            changed()
        }
        val decoder=java.util.Base64.getDecoder()
        images.replaceAll(snapshot.exerciseImages.mapValues{decoder.decode(it.value)})
    }
}

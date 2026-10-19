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
    private val db=Room.databaseBuilder(context.applicationContext,HealthDatabase::class.java,name).build()
    private val dao=db.dao()
    private val json=Json{encodeDefaults=true}
    val snapshots:Flow<UserSnapshot> = dao.observe().map(::decode)
    val revisions:Flow<Revision> = dao.observeRevision().map{it?:Revision()}
    private fun decode(rows:List<StoredRecord>):UserSnapshot = UserSnapshot(
        profile=rows.find{it.kind=="profile"}?.let{json.decodeFromString<Profile>(it.payload)},
        goals=rows.filter{it.kind=="goal"}.map{json.decodeFromString<DailyGoals>(it.payload)},
        entries=rows.filter{it.kind=="entry"}.map{json.decodeFromString<DiaryEntry>(it.payload)},
        customFoods=rows.filter{it.kind=="food"}.map{json.decodeFromString<Food>(it.payload)},
        completedDays=rows.filter{it.kind=="day"}.map{it.payload}.toSet(),onboarded=rows.any{it.kind=="onboarded"})
    private suspend fun changed() { val r=dao.revision()?:Revision();dao.putRevision(r.copy(current=r.current+1)) }
    suspend fun exportSnapshot():UserSnapshot = db.withTransaction{decode(dao.all())}
    suspend fun exportVersioned():Pair<Long,UserSnapshot> = db.withTransaction{Pair((dao.revision()?:Revision()).current,decode(dao.all()))}
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
    suspend fun completeDay(date:String,completed:Boolean) { LocalDate.parse(date);db.withTransaction{if(completed)dao.put(StoredRecord("day:$date","day",date))else dao.delete("day:$date");changed()} }
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
            changed()
        }
    }
    fun observeDay(date:LocalDate):Flow<DayState> = snapshots.map{it.day(date.toString())}
    fun observeWeek(endDate:LocalDate):Flow<List<DayState>> = snapshots.map{it.week(endDate.toString())}
}

package az.healthchecker.data
import androidx.room.withTransaction
import az.healthchecker.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Device-only unfinished meal; favorites and named meals are ordinary backup-visible records. */
class MealsStore(private val db:HealthDatabase,private val dao:HealthDao,private val json:Json,private val changed:suspend ()->Unit) {
    val drafts:Flow<MealDraft?> = dao.observe().map{rows->rows.find{it.key=="meal-draft"}?.let{json.decodeFromString<MealDraft>(it.payload)}}.distinctUntilChanged()
    suspend fun draft():MealDraft?=dao.get("meal-draft")?.let{json.decodeFromString(it.payload)}
    private suspend fun put(draft:MealDraft){validateMealDraft(draft);dao.put(StoredRecord("meal-draft","internal",json.encodeToString(draft)))}
    suspend fun append(items:List<MealItem>,date:String):MealDraft=db.withTransaction {
        validateMealItems(items)
        val old=draft()
        val next=MealDraft(old?.id?:UUID.randomUUID().toString(),old?.date?:date,((old?.items?:emptyList())+items).associateBy{it.id}.values.toList())
        put(next);next
    }
    suspend fun update(id:String,q:Quantity)=db.withTransaction { draft()?.let{d->put(d.copy(items=d.items.map{if(it.id==id)it.copy(quantity=q)else it}))} }
    suspend fun remove(id:String)=db.withTransaction { draft()?.let{d->val next=d.copy(items=d.items.filter{it.id!=id});if(next.items.isEmpty())dao.delete("meal-draft")else put(next)} }
    suspend fun clear()=db.withTransaction{dao.delete("meal-draft")}
    private suspend fun record(draft:MealDraft,now:Instant,zone:ZoneId):List<DiaryEntry> {
        val entries=draft.entries(now,zone)
        validateSnapshot(UserSnapshot(entries=entries))
        require(entries.none{dao.get("entry:${it.id}")!=null}){tr("Этот приём пищи уже записан","This meal has already been logged")}
        entries.forEach{dao.put(StoredRecord("entry:${it.id}","entry",json.encodeToString(it)))}
        dao.delete("meal-draft")
        if(entries.isNotEmpty())changed()
        return entries
    }
    suspend fun commit(id:String,now:Instant,zone:ZoneId):List<DiaryEntry> = db.withTransaction {
        val draft=draft()?.takeIf{it.id==id}?:return@withTransaction emptyList()
        record(draft,now,zone)
    }
    suspend fun log(item:MealItem,date:String,now:Instant,zone:ZoneId):List<DiaryEntry> = db.withTransaction {
        validateMealItems(listOf(item))
        if(dao.get("entry:${item.id}")!=null)return@withTransaction emptyList()
        val old=draft()
        val next=MealDraft(old?.id?:UUID.randomUUID().toString(),old?.date?:date,((old?.items?:emptyList())+item).associateBy{it.id}.values.toList())
        record(next,now,zone)
    }
    suspend fun favorite(id:String,enabled:Boolean)=db.withTransaction {
        require(id.length in 1..200)
        val key="favorite:$id"
        if((dao.get(key)!=null)==enabled)return@withTransaction
        if(enabled)require(dao.countKind("favorite")<10000){tr("Слишком много избранных продуктов","Too many favorite foods")}
        if(enabled)dao.put(StoredRecord(key,"favorite",id))else dao.delete(key)
        changed()
    }
    suspend fun save(meal:SavedMeal) {validateSavedMeal(meal);db.withTransaction{
        require(dao.get("meal:${meal.id}")!=null||dao.countKind("meal")<200){tr("Можно сохранить до 200 наборов","You can save up to 200 meals")}
        dao.put(StoredRecord("meal:${meal.id}","meal",json.encodeToString(meal)));changed()
    } }
    suspend fun delete(id:String)=db.withTransaction{dao.delete("meal:$id");changed()}
    suspend fun deleteEntries(ids:List<String>)=db.withTransaction{if(ids.isNotEmpty()){ids.forEach{dao.delete("entry:$it")};changed()}}
}

package az.healthchecker.domain
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** A product snapshot, so later catalog or recipe changes cannot rewrite a saved breakfast. */
@Serializable data class MealItem(val food:Food,val quantity:Quantity,val id:String=UUID.randomUUID().toString())
@Serializable data class SavedMeal(val id:String,val name:String,val items:List<MealItem>)
@Serializable data class MealDraft(val id:String,val date:String,val items:List<MealItem>) {
    fun entries(now:Instant,zone:ZoneId):List<DiaryEntry> {
        validateMealDraft(this)
        return items.map{makeEntry(it.id,it.food,it.quantity,date,null,now,zone)}
    }
}
fun foodSnapshot(food:Food):Food = food.copy(aliases=emptyList(),recipe=null,common=false)
fun validateMealItems(items:List<MealItem>,allowEmpty:Boolean=false) {
    require(items.size in (if(allowEmpty)0 else 1)..100){tr("В приёме пищи может быть от 1 до 100 продуктов","A meal can contain 1 to 100 foods")}
    require(items.map{it.id}.toSet().size==items.size){tr("Повторяющиеся продукты в наборе","Duplicate meal item identifiers")}
    items.forEach {
        require(UUID.fromString(it.id).toString()==it.id)
        require(it.food.id.length in 1..200 && it.food.name.isNotBlank() && it.food.name.length<=500)
        calculate(it.food,it.quantity)
    }
}
fun validateMealDraft(draft:MealDraft) { require(UUID.fromString(draft.id).toString()==draft.id);LocalDate.parse(draft.date);validateMealItems(draft.items,true) }
fun validateSavedMeal(meal:SavedMeal) {
    require(meal.id.startsWith("meal:") && meal.id.length<=100 && meal.name.isNotBlank() && meal.name.length<=100){tr("Введите название набора, до 100 символов","Enter a meal name, up to 100 characters")}
    validateMealItems(meal.items)
}

package az.healthchecker.domain
import kotlinx.serialization.Serializable

/** Ingredient snapshot: a dish keeps its numbers even if the catalog product changes later. */
@Serializable data class Ingredient(val foodId:String,val name:String,val quantity:Quantity,val grams:Double,val nutrients:Nutrients)
/** [cookedGrams] is the weighed finished dish; without it the raw ingredient weight is used. [icon] is the inventory picture key. */
@Serializable data class Recipe(val ingredients:List<Ingredient>,val cookedGrams:Double?=null,val servings:Int?=null,val icon:String="plate")

fun ingredientOf(food:Food,quantity:Quantity):Ingredient { val c=calculate(food,quantity);return Ingredient(food.id,food.name,quantity,c.grams,c.nutrients) }

data class DishTotals(val rawGrams:Double,val totalGrams:Double,val nutrients:Nutrients)
fun dishTotals(recipe:Recipe):DishTotals {
    val list=recipe.ingredients
    // One unknown ingredient value makes the dish value unknown rather than silently low.
    fun sum(select:(Nutrients)->Double?):Double? = if(list.any{select(it.nutrients)==null})null else list.sumOf{select(it.nutrients)!!}
    val raw=list.sumOf{it.grams}
    return DishTotals(raw,recipe.cookedGrams?:raw,Nutrients(sum{it.kcal},sum{it.protein},sum{it.fat},sum{it.carbs}))
}

fun buildDish(id:String,name:String,recipe:Recipe):Food {
    require(name.isNotBlank()&&name.length<=200){"Введите название блюда"}
    require(recipe.ingredients.isNotEmpty()){"Добавьте хотя бы один ингредиент"}
    require(recipe.ingredients.size<=100){"Не больше 100 ингредиентов"}
    require(recipe.cookedGrams==null||recipe.cookedGrams.isFinite()&&recipe.cookedGrams>0&&recipe.cookedGrams<=200000){"Проверьте вес готового блюда"}
    require(recipe.servings==null||recipe.servings in 1..100){"Порций может быть от 1 до 100"}
    val t=dishTotals(recipe)
    require(t.nutrients.kcal!=null){"У одного из ингредиентов неизвестна калорийность"}
    fun per100(v:Double?)=v?.times(100/t.totalGrams)
    val per100g=Nutrients(per100(t.nutrients.kcal),per100(t.nutrients.protein),per100(t.nutrients.fat),per100(t.nutrients.carbs))
    require(per100g.kcal!!<=1000){"Слишком калорийно на 100 г — проверьте вес готового блюда"}
    val portions=listOf(Portion("Всё блюдо",t.totalGrams))+(recipe.servings?.takeIf{it>1}?.let{listOf(Portion("Порция (1/$it)",t.totalGrams/it))}?:emptyList())
    return Food(id,name.trim(),aliases=listOf("блюдо рецепт"),per100g=per100g,portions=portions,source="Своё блюдо · ингредиентов: ${recipe.ingredients.size}",recipe=recipe)
}

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
    return DishTotals(raw,recipe.cookedGrams?:raw,Nutrients(sum{it.kcal},sum{it.protein},sum{it.fat},sum{it.carbs},sum{it.fiber}))
}

fun buildDish(id:String,name:String,recipe:Recipe):Food {
    require(name.isNotBlank()&&name.length<=200){tr("Введите название блюда","Enter the dish name")}
    require(recipe.ingredients.isNotEmpty()){tr("Добавьте хотя бы один ингредиент","Add at least one ingredient")}
    require(recipe.ingredients.size<=100){tr("Не больше 100 ингредиентов","No more than 100 ingredients")}
    require(recipe.cookedGrams==null||recipe.cookedGrams.isFinite()&&recipe.cookedGrams>0&&recipe.cookedGrams<=200000){tr("Проверьте вес готового блюда","Check the cooked weight")}
    require(recipe.servings==null||recipe.servings in 1..100){tr("Порций может быть от 1 до 100","Servings must be 1 to 100")}
    val t=dishTotals(recipe)
    require(t.nutrients.kcal!=null){tr("У одного из ингредиентов неизвестна калорийность","One of the ingredients has unknown calories")}
    val per100g=t.nutrients.times(100/t.totalGrams)
    require(per100g.kcal!!<=1000){tr("Слишком калорийно на 100 г — проверьте вес готового блюда","Too many calories per 100 g — check the cooked weight")}
    // Portion labels are stored in both languages, like catalog portions.
    val portions=listOf(Portion("Всё блюдо",t.totalGrams,"Whole dish"))+(recipe.servings?.takeIf{it>1}?.let{listOf(Portion("Порция (1/$it)",t.totalGrams/it,"Serving (1/$it)"))}?:emptyList())
    return Food(id,name.trim(),aliases=listOf("блюдо рецепт dish recipe"),per100g=per100g,portions=portions,source="Своё блюдо · ингредиентов: ${recipe.ingredients.size}",recipe=recipe)
}

package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
class RecipeTest {
    private val chicken=Food("usda:chicken","Куриная грудка, сырая",per100g=Nutrients(120.0,22.5,2.6,0.0))
    private val carrot=Food("usda:carrot","Морковь",per100g=Nutrients(41.0,0.93,0.24,9.58))
    private val potato=Food("usda:potato","Картофель",per100g=Nutrients(77.0,2.0,0.1,17.0),portions=listOf(Portion("Средний",150.0)))
    private val greens=Food("usda:parsley","Петрушка",per100g=Nutrients(36.0,null,0.8,6.3))
    private fun recipe(cooked:Double?=null,servings:Int?=null)=Recipe(listOf(
        ingredientOf(chicken,Quantity(500.0,Measure.GRAMS)),ingredientOf(carrot,Quantity(150.0,Measure.GRAMS)),
        ingredientOf(potato,Quantity(5.0,Measure.PIECES,150.0)),ingredientOf(greens,Quantity(1.0,Measure.PIECES,30.0))),cooked,servings)

    @Test fun dishSumsIngredientsAndScalesToEatenGrams() {
        val t=dishTotals(recipe());assertEquals(1430.0,t.rawGrams,1e-9);assertEquals(1249.8,t.nutrients.kcal!!,1e-9)
        val dish=buildDish("custom:dish:1","Курица с овощами",recipe(cooked=1300.0))
        assertEquals(1249.8*100/1300,dish.per100g.kcal!!,1e-9)
        // Eating 300 g of the finished dish takes 300/1300 of everything that went in.
        assertEquals(1249.8*300/1300,calculate(dish,Quantity(300.0,Measure.GRAMS)).nutrients.kcal!!,1e-9)
        assertEquals(listOf(Portion("Всё блюдо",1300.0)),dish.portions)
    }
    @Test fun rawWeightIsUsedWithoutCookedWeight() { assertEquals(1249.8*100/1430,buildDish("custom:dish:1","Блюдо",recipe()).per100g.kcal!!,1e-9) }
    @Test fun unknownIngredientValueMakesDishValueUnknown() { val d=buildDish("custom:dish:1","Блюдо",recipe());assertNull(d.per100g.protein);assertNotNull(d.per100g.fat) }
    @Test fun servingsBecomeAPiecePortion() {
        val d=buildDish("custom:dish:1","Блюдо",recipe(cooked=1200.0,servings=4))
        assertEquals(Portion("Порция (1/4)",300.0),d.portions[1])
        assertEquals(1249.8/4,calculate(d,Quantity(1.0,Measure.PIECES,300.0)).nutrients.kcal!!,1e-9)
    }
    @Test fun invalidDishesAreRejected() {
        assertThrows(IllegalArgumentException::class.java){buildDish("custom:dish:1","Пусто",Recipe(emptyList()))}
        assertThrows(IllegalArgumentException::class.java){buildDish("custom:dish:1"," ",recipe())}
        assertThrows(IllegalArgumentException::class.java){buildDish("custom:dish:1","Блюдо",recipe(cooked=50.0))}
        assertThrows(IllegalArgumentException::class.java){buildDish("custom:dish:1","Блюдо",recipe(servings=0))}
    }
    @Test fun dishPassesSnapshotValidationAndIsStoredWithItsRecipe() {
        val d=buildDish("custom:dish:1","Блюдо",recipe(cooked=1300.0,servings=2))
        validateSnapshot(UserSnapshot(customFoods=listOf(d)))
        assertEquals(4,d.recipe!!.ingredients.size);assertEquals(750.0,d.recipe!!.ingredients[2].grams,1e-9)
    }
}

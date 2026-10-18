package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
class NutritionCalculatorTest {
    private val food = Food("test", "Food", per100g=Nutrients(200.0,10.0,4.0,20.0))
    @Test fun gramsScaleAllNutrients() { assertEquals(Nutrients(100.0,5.0,2.0,10.0), calculate(food,Quantity(50.0,Measure.GRAMS)).nutrients) }
    @Test fun portionsUseEditableMass() { assertEquals(240.0,calculate(food,Quantity(2.0,Measure.PIECES,60.0)).nutrients.kcal!!,0.0001); assertEquals(60.0,calculate(food,Quantity(0.5,Measure.PIECES,60.0)).nutrients.kcal!!,0.0001) }
    @Test fun acceptsDecimalComma() { assertEquals(1.5,parseQuantity("1,5"),0.0) }
    @Test fun rejectsInvalidQuantities() { listOf("0","-1","NaN","Infinity","", "1e999").forEach { s -> assertThrows(IllegalArgumentException::class.java) { parseQuantity(s) } } }
    @Test fun requiresDensityForMl() { assertThrows(IllegalArgumentException::class.java) { calculate(food,Quantity(100.0,Measure.ML)) } }
    @Test fun unknownDoesNotBecomeZero() { assertNull(calculate(food.copy(per100g=Nutrients(200.0)),Quantity(50.0,Measure.GRAMS)).nutrients.protein) }
}

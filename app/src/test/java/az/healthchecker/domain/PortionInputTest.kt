package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
class PortionInputTest {
    private val egg=Food("egg","Egg",per100g=Nutrients(155.0),portions=listOf(Portion("Egg",50.0)))
    @Test fun switchingUnitsKeepsTheWeighedMassAndFractionalPieces() {
        val grams=Quantity(125.0,Measure.GRAMS)
        val pieces=convertQuantity(egg,grams,Measure.PIECES,50.0)
        assertEquals(Quantity(2.5,Measure.PIECES,50.0),pieces)
        assertEquals(grams,convertQuantity(egg,pieces,Measure.GRAMS))
        assertEquals(pieces,convertQuantity(egg,pieces,Measure.PIECES,75.0))
    }
    @Test fun liquidConversionUsesDensityAndDoesNotInventAPieceWeight() {
        val oil=egg.copy(density=0.8,portions=emptyList())
        assertEquals(Quantity(30.0,Measure.ML),convertQuantity(oil,Quantity(24.0,Measure.GRAMS),Measure.ML))
        assertEquals(Quantity(24.0,Measure.GRAMS),convertQuantity(oil,Quantity(30.0,Measure.ML),Measure.GRAMS))
        assertThrows(IllegalArgumentException::class.java){convertQuantity(oil,Quantity(24.0,Measure.GRAMS),Measure.PIECES)}
    }
    @Test fun preparationLabelsUseWholeWordsAndKeepEstimatesDistinct() {
        assertEquals(setOf(FoodPreparation.RAW),foodPreparations(egg.copy(nameEn="Chicken breast, raw")))
        assertEquals(setOf(FoodPreparation.COOKED),foodPreparations(egg.copy(nameEn="Egg, cooked, hard-boiled")))
        assertEquals(setOf(FoodPreparation.DRY),foodPreparations(egg.copy(nameEn="Oats, dry")))
        assertTrue(foodPreparations(egg.copy(nameEn="Strawberries")).isEmpty())
    }
}

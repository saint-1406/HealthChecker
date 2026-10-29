package az.healthchecker.domain

import az.healthchecker.barcode.*
import org.junit.Assert.*
import org.junit.Test

class BarcodeTest {
    @Test fun denseVolumeNutritionIsNormalizedOnlyAfterMeasuredDensity() {
        val label=LabelProduct(normalizeBarcode("4006381333931"),"Syrup",nutrients=Nutrients(448.0,carbs=112.0),basis=NutritionBasis.VOLUME)
        val food=label.toFood(NutritionBasis.VOLUME,140.0)
        assertEquals(80.0,food.per100g.carbs!!,1e-8)
        val portion=calculate(food,Quantity(200.0,Measure.ML))
        assertEquals(896.0,portion.nutrients.kcal!!,1e-8)
        assertEquals(224.0,portion.nutrients.carbs!!,1e-8)
    }
    @Test fun canonicalCodesPreserveLeadingZerosAndUnifyUpcAliases() {
        assertEquals("00012345678905",normalizeBarcode("012345678905"))
        assertEquals(normalizeBarcode("012345678905"),normalizeBarcode("0012345678905"))
        assertEquals("04006381333931",normalizeBarcode("4006381333931"))
        assertEquals("00000096385074",normalizeBarcode("96385074"))
        assertEquals("04006381333931",normalizeBarcode("04006381333931"))
    }
    @Test fun invalidNumbersUrlsAndWrongChecksumsAreRejected() {
        listOf("4006381333932","0000000000000","123","https://example.com/4006381333931","4e12","+4006381333931").forEach {
            assertThrows(IllegalArgumentException::class.java){normalizeBarcode(it)}
        }
    }
    @Test fun volumeLabelRequiresMeasuredDensityAndKeepsCaloriesCorrect() {
        val label=LabelProduct(normalizeBarcode("4006381333931"),"Milk","Brand",Nutrients(60.0,3.0),500.0,NutritionBasis.VOLUME)
        assertThrows(IllegalArgumentException::class.java){label.toFood(NutritionBasis.VOLUME,null)}
        val food=label.toFood(NutritionBasis.VOLUME,103.0)
        val result=calculate(food,Quantity(200.0,Measure.ML))
        assertEquals(120.0,result.nutrients.kcal!!,1e-8);assertEquals(206.0,result.grams,1e-8)
        assertEquals(515.0,food.portions.single().grams,1e-8)
        assertEquals(label.code,food.barcode)
    }
}

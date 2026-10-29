package az.healthchecker.domain

import az.healthchecker.barcode.*
import org.junit.Assert.*
import org.junit.Test

class BarcodeTest {
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
    private fun response(nutrients:String,per:String="100g",preparation:String="as_sold",unit:String="g")="""{"code":"4006381333931","status":"success","product":{"code":"4006381333931","product_name":"Test","brands":"Brand","product_quantity":500,"product_quantity_unit":"$unit","nutrition":{"aggregated_set":{"per":"$per","preparation":"$preparation","nutrients":{$nutrients}}}}}"""
    private fun n(name:String,value:String,unit:String="g",source:String="packaging")=""""$name":{"value":$value,"unit":"$unit","source":"$source"}"""
    @Test fun parserUsesLabelNumbersNotEstimatesAndConvertsKjOnlyWhenKcalMissing() {
        val p=OpenFoodFacts.parse("4006381333931",response(listOf(n("energy-kj","836.8","kJ"),n("proteins","5"),n("fat","99",source="estimate")).joinToString(",")))!!
        assertEquals(200.0,p.nutrients.kcal!!,0.0001);assertEquals(5.0,p.nutrients.protein!!,0.0)
        assertNull(p.nutrients.fat);assertNull(p.nutrients.carbs);assertEquals("Test",p.name)
        assertEquals(500.0,p.packageAmount!!,0.0)
    }
    @Test fun servingsPreparedValuesAndInvalidNutrientsDoNotBecomePer100g() {
        val data=n("energy-kcal","200","kcal")
        assertNull(OpenFoodFacts.parse("4006381333931",response(data,"serving"))!!.nutrients.kcal)
        assertNull(OpenFoodFacts.parse("4006381333931",response(data,preparation="prepared"))!!.nutrients.kcal)
        val invalid=response(n("energy-kcal","-1","kcal")+","+n("proteins","10000"))
        val p=OpenFoodFacts.parse("4006381333931",invalid)!!
        assertNull(p.nutrients.kcal);assertNull(p.nutrients.protein)
    }
    @Test fun wrongProductNeverImportsAndNotFoundIsDistinct() {
        assertThrows(IllegalArgumentException::class.java){OpenFoodFacts.parse("3017620422003",response(""))}
        assertNull(OpenFoodFacts.parse("4006381333931","""{"status":"failure","product":null}"""))
        assertThrows(IllegalArgumentException::class.java){OpenFoodFacts.parse("4006381333931","not json")}
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

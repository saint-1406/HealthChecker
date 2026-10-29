package az.healthchecker.barcode

import az.healthchecker.domain.*
import kotlinx.serialization.Serializable

@Serializable enum class NutritionBasis { MASS, VOLUME }

/** Numbers on the label, before explicit mass/volume normalization by the user. */
@Serializable data class LabelProduct(val code:String,val name:String="",val brand:String="",val nutrients:Nutrients=Nutrients(),val packageAmount:Double?=null,val basis:NutritionBasis=NutritionBasis.MASS,val fromOpenFoodFacts:Boolean=false) {
    fun toFood(basis:NutritionBasis,gramsPer100Ml:Double?=null):Food {
        val canonical=normalizeBarcode(code)
        val density=if(basis==NutritionBasis.VOLUME) {
            require(gramsPer100Ml!=null&&gramsPer100Ml.isFinite()&&gramsPer100Ml in 1.0..2000.0){tr("Укажите вес 100 мл продукта в граммах","Enter the weight of 100 ml of this product in grams")}
            gramsPer100Ml/100.0
        }else null
        val pack=packageAmount?.also{require(it.isFinite()&&it>0&&it<=100000){tr("Проверьте размер упаковки","Check the package size")}}?.times(density?:1.0)
        val title=listOf(name.trim(),brand.trim()).filter{it.isNotEmpty()}.distinct().joinToString(" · ")
        val f=Food("custom:barcode:$canonical",title,per100g=nutrients.times(1.0/(density?:1.0)),
            portions=pack?.let{listOf(Portion("Упаковка",it,"Package"))}?:emptyList(),density=density,
            source=if(fromOpenFoodFacts)"Open Food Facts · ODbL · https://world.openfoodfacts.org/product/$canonical" else tr("Пользователь · этикетка","User · packaging label"),barcode=canonical)
        validateSnapshot(UserSnapshot(customFoods=listOf(f)))
        require(listOf(f.per100g.protein,f.per100g.fat,f.per100g.carbs,f.per100g.fiber).all{it==null||it in 0.0..(100.0+1e-8)}){tr("Проверьте БЖУ и основу расчёта","Check macros and the nutrition basis")}
        return f
    }
}

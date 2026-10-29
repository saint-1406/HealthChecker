package az.healthchecker.barcode

import az.healthchecker.domain.*
import kotlinx.serialization.json.*

enum class NutritionBasis { MASS, VOLUME }

/** Numbers on the label, before explicit mass/volume normalization by the user. */
data class LabelProduct(val code:String,val name:String="",val brand:String="",val nutrients:Nutrients=Nutrients(),val packageAmount:Double?=null,val basis:NutritionBasis=NutritionBasis.MASS,val fromOpenFoodFacts:Boolean=false) {
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

object OpenFoodFacts {
    private val json=Json{ignoreUnknownKeys=true}
    fun parse(requestedCode:String,text:String):LabelProduct? {
        val root=json.parseToJsonElement(text).jsonObject
        val p=(root["product"] as? JsonObject)?.takeIf{it.isNotEmpty()}?:return null
        val code=normalizeBarcode(p.string("code").ifBlank{root.string("code")})
        require(code==normalizeBarcode(requestedCode)){tr("Ответ содержит другой штрихкод","The response contains a different barcode")}
        val set=(p["nutrition"] as? JsonObject)?.get("aggregated_set") as? JsonObject
        val per=set?.string("per")
        val raw=if(set?.string("preparation")=="as_sold"&&per in listOf("100g","100ml"))set["nutrients"] as? JsonObject else null
        fun value(key:String,unit:String,max:Double):Double? {
            val n=raw?.get(key) as? JsonObject?:return null
            if(n.string("source") !in listOf("packaging","manufacturer")||n.string("unit")!=unit)return null
            return n.number("value")?.takeIf{it in 0.0..max}
        }
        val kcal=if(raw?.get("energy-kcal")!=null)value("energy-kcal","kcal",1000.0)else value("energy-kj","kJ",4184.0)?.div(4.184)
        val packageUnit=p.string("product_quantity_unit").lowercase()
        val factor=when(packageUnit){"g","ml"->1.0;"kg","l"->1000.0;else->null}
        val basis=if(per=="100ml")NutritionBasis.VOLUME else NutritionBasis.MASS
        // A package can be sold by volume while its nutrition is declared by mass (and vice versa).
        // Do not relabel either measurement; incompatible package quantities need manual confirmation.
        val compatible=if(basis==NutritionBasis.VOLUME)packageUnit in listOf("ml","l")else packageUnit in listOf("g","kg")
        val amount=if(compatible)factor?.let{p.number("product_quantity")?.times(it)}?.takeIf{it>0&&it<=100000}else null
        return LabelProduct(code,p.string("product_name").take(150),p.string("brands").take(45),
            Nutrients(kcal,value("proteins","g",100.0),value("fat","g",100.0),value("carbohydrates","g",100.0),value("fiber","g",100.0)),amount,basis,true)
    }
    private fun JsonObject.string(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.number(key:String)=(get(key) as? JsonPrimitive)?.doubleOrNull?.takeIf{it.isFinite()}
}

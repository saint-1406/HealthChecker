package az.healthchecker.domain
import java.util.Locale

/** Changing only a unit preserves the actual amount eaten; tapping the selected unit is a no-op. */
fun convertQuantity(food:Food,q:Quantity,target:Measure,pieceGrams:Double?=null):Quantity {
    if(q.unit==target)return q
    val grams=calculate(food,q).grams
    val converted=when(target) {
        Measure.GRAMS->Quantity(grams,Measure.GRAMS)
        Measure.ML->{val density=food.density;require(density!=null&&density.isFinite()&&density>0);Quantity(grams/density,Measure.ML)}
        Measure.PIECES->{val p=pieceGrams?:q.portionGrams?:food.portions.firstOrNull()?.grams;require(p!=null&&p.isFinite()&&p>0){tr("Сначала укажите вес одной штуки","Enter the weight of one piece first")};Quantity(grams/p,Measure.PIECES,p)}
    }
    calculate(food,converted)
    return converted
}
enum class FoodPreparation { RAW, COOKED, DRY }
private val PREPARATION_WORDS=Regex("[^a-z]+")
fun foodPreparations(food:Food):Set<FoodPreparation> {
    val words=food.nameEn.orEmpty().lowercase(Locale.ROOT).split(PREPARATION_WORDS).toSet()
    return buildSet {
        if(words.any{it in setOf("raw","uncooked")})add(FoodPreparation.RAW)
        if(words.any{it in setOf("cooked","boiled","roasted","stewed","fried","baked","steamed","grilled")})add(FoodPreparation.COOKED)
        if(words.any{it in setOf("dry","dried","dehydrated")})add(FoodPreparation.DRY)
    }
}
enum class FoodFilter { ALL, FAVORITES, OWN }
@kotlinx.serialization.Serializable data class OverviewCards(val weight:Boolean=true,val steps:Boolean=true,val training:Boolean=true)

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
private val RAW_PREPARATION=Regex("^(raw|uncooked)(?=$|\\s*\\()")
private val COOKED_PREPARATION=Regex("^(cooked|boiled|roasted|stewed|fried|baked|steamed|grilled|prepared|dry[ -]roasted)\\b")
private val DRY_PREPARATION=Regex("^(dry|dried|dehydrated)\\b")
fun foodPreparations(food:Food):Set<FoodPreparation> {
    // USDA uses comma-separated preparation clauses. A raw onion in a sandwich is not a raw sandwich,
    // and a soup prepared from dry mix is no longer dry. Unclear names receive no inferred label.
    val clauses=food.nameEn.orEmpty().lowercase(Locale.ROOT).split(',').map(String::trim)
    val cooked=clauses.any{COOKED_PREPARATION.containsMatchIn(it)}
    return buildSet {
        if(cooked)add(FoodPreparation.COOKED)
        else {
            if(clauses.any{RAW_PREPARATION.containsMatchIn(it)})add(FoodPreparation.RAW)
            if(clauses.any{DRY_PREPARATION.containsMatchIn(it)})add(FoodPreparation.DRY)
        }
    }
}
enum class FoodFilter { ALL, FAVORITES, OWN }
@kotlinx.serialization.Serializable data class OverviewCards(val weight:Boolean=true,val steps:Boolean=true,val training:Boolean=true)

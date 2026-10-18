package az.healthchecker.domain
import kotlinx.serialization.Serializable
@Serializable data class DiaryEntry(
    val id: String, val foodId: String, val name: String, val quantity: Quantity,
    val grams: Double, val nutrients: Nutrients, val waterMl: Double = 0.0,
    val instant: String, val zoneId: String, val localDate: String
)
data class NutrientTotal(val value: Double, val complete: Boolean)
data class DaySummary(val kcal: NutrientTotal, val protein: NutrientTotal, val fat: NutrientTotal, val carbs: NutrientTotal, val waterMl: Double)
fun summarize(entries: List<DiaryEntry>): DaySummary {
    fun total(select:(Nutrients)->Double?):NutrientTotal = NutrientTotal(entries.sumOf { select(it.nutrients)?:0.0 },entries.all { select(it.nutrients)!=null })
    return DaySummary(total{it.kcal},total{it.protein},total{it.fat},total{it.carbs},entries.sumOf{it.waterMl})
}

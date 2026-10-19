package az.healthchecker.domain
import kotlinx.serialization.Serializable
@Serializable data class Nutrients(val kcal: Double? = null, val protein: Double? = null, val fat: Double? = null, val carbs: Double? = null)
@Serializable data class Portion(val name: String, val grams: Double)
@Serializable data class Food(val id: String, val name: String, val aliases: List<String> = emptyList(), val per100g: Nutrients, val portions: List<Portion> = emptyList(), val density: Double? = null, val source: String = "Пользователь", val isWater: Boolean = false, val recipe: Recipe? = null)
@Serializable enum class Measure { GRAMS, PIECES, ML }
@Serializable data class Quantity(val value: Double, val unit: Measure, val portionGrams: Double? = null)
data class CalculatedPortion(val grams: Double, val nutrients: Nutrients)

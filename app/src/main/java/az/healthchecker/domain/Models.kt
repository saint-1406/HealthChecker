@file:OptIn(ExperimentalSerializationApi::class)
package az.healthchecker.domain
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
// Fields added after schema 2 are written only when set, so older records keep their exact shape.
@Serializable data class Nutrients(val kcal: Double? = null, val protein: Double? = null, val fat: Double? = null, val carbs: Double? = null, @EncodeDefault(EncodeDefault.Mode.NEVER) val fiber: Double? = null)
@Serializable data class Portion(val name: String, val grams: Double, @EncodeDefault(EncodeDefault.Mode.NEVER) val nameEn: String? = null)
/** [name] is Russian, [nameEn] the English name of catalog foods; the user's own foods have one name. [common] marks everyday catalog foods. */
@Serializable data class Food(val id: String, val name: String, val aliases: List<String> = emptyList(), val per100g: Nutrients, val portions: List<Portion> = emptyList(), val density: Double? = null, val source: String = "Пользователь", val isWater: Boolean = false, val recipe: Recipe? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val nameEn: String? = null, @EncodeDefault(EncodeDefault.Mode.NEVER) val sourceEn: String? = null, @EncodeDefault(EncodeDefault.Mode.NEVER) val common: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val barcode:String? = null)
@Serializable enum class Measure { GRAMS, PIECES, ML }
@Serializable data class Quantity(val value: Double, val unit: Measure, val portionGrams: Double? = null)
data class CalculatedPortion(val grams: Double, val nutrients: Nutrients)
/** Every nutrient multiplied; an unknown value stays unknown. */
fun Nutrients.times(k:Double):Nutrients = Nutrients(kcal?.times(k),protein?.times(k),fat?.times(k),carbs?.times(k),fiber?.times(k))

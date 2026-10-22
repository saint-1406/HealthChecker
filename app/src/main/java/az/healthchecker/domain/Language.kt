package az.healthchecker.domain
import java.util.Locale

/** The app speaks Russian by default; the user can switch to English in the profile. */
enum class AppLanguage(val tag:String) { RU("ru"), EN("en") }

object Lang {
    /** Set once at start from the saved choice and on every switch; the UI is rebuilt when it changes. */
    @Volatile var current:AppLanguage = AppLanguage.RU
    val locale:Locale get() = Locale.forLanguageTag(current.tag)
    val en:Boolean get() = current==AppLanguage.EN
}

/** Text in the current language. Both versions sit next to each other so neither can be forgotten. */
fun tr(ru:String,en:String):String = if(Lang.en)en else ru

/** "1 запись / 2 записи / 5 записей" and "1 entry / 2 entries". */
fun count(n:Int,one:String,few:String,many:String,enOne:String,enMany:String):String =
    "$n "+if(Lang.en)(if(n==1)enOne else enMany) else when{n%100 in 11..14->many;n%10==1->one;n%10 in 2..4->few;else->many}

val Food.displayName:String get() = if(Lang.en)nameEn?:name else name
val Food.displaySource:String get() = when {
    recipe!=null -> tr("Своё блюдо · ингредиентов: ${recipe.ingredients.size}","Your dish · ingredients: ${recipe.ingredients.size}")
    id.startsWith("custom:") -> tr("Свой продукт","Your own food")
    else -> if(Lang.en)sourceEn?:source else source
}
val Portion.displayName:String get() = if(Lang.en)nameEn?:name else name
val Exercise.displayName:String get() = if(Lang.en)nameEn?:name else name
val Exercise.displayCue:String get() = if(Lang.en)cueEn?:cue else cue

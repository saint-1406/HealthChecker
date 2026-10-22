package az.healthchecker.data
import androidx.core.content.edit
import android.content.Context
import android.content.res.Configuration
import az.healthchecker.domain.AppLanguage
import az.healthchecker.domain.Lang

/**
 * The interface language. Kept in plain preferences because it is needed synchronously, before the first screen is
 * drawn, so the app never flashes in the other language.
 */
object LanguageStore {
    private const val PREFS="app";private const val KEY="language"
    fun load(context:Context):AppLanguage = context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,null)?.let{v->AppLanguage.entries.find{it.tag==v}}?:AppLanguage.RU
    fun save(context:Context,language:AppLanguage) { context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit{putString(KEY,language.tag)};Lang.current=language }
    /** Applies the saved language and returns a context whose resources and locale follow it. */
    fun wrap(context:Context):Context {
        Lang.current=load(context)
        val config=Configuration(context.resources.configuration).apply{setLocale(Lang.locale)}
        return context.createConfigurationContext(config)
    }
}

package com.dataespresso.squarechess

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** A language the app has translations for. [tag] is a BCP 47 tag; its name is written in that language. */
data class AppLanguage(val tag: String, val name: String)

val APP_LANGUAGES = listOf(
    AppLanguage("en", "English"),
    AppLanguage("zh-CN", "简体中文"),
    AppLanguage("nb", "Norsk bokmål"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("es", "Español"),
    AppLanguage("fr", "Français")
)

/**
 * The app language: the phone's language by default, or one chosen in Settings.
 * Android 13+ keeps the choice itself (it also shows under the system's App languages);
 * on Android 10–12 the app stores it and applies it to each activity.
 */
object LanguageSetting {
    private const val PREFS = "settings"
    private const val KEY = "language"

    /** The chosen tag, or "" for the phone's language. */
    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            // The system restarts the activity in the new language.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            activity.recreate()
        }
    }

    /** Android 10–12: the activity context with the chosen language applied. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        // Dates and number formats follow the app language too.
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** Which entry of [APP_LANGUAGES] is chosen, or null for the phone's language. */
    fun chosen(context: Context): AppLanguage? {
        val tag = current(context).substringBefore(',')
        if (tag.isEmpty()) return null
        val locale = Locale.forLanguageTag(tag)
        return APP_LANGUAGES.firstOrNull { Locale.forLanguageTag(it.tag).language == locale.language }
    }
}

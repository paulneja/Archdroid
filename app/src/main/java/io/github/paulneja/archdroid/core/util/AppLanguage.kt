package io.github.paulneja.archdroid.core.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

@SuppressLint("AppBundleLocaleChanges") // Distributed as a universal APK with every language inside.
object AppLanguage {

    enum class Choice(val tag: String) { SYSTEM(""), ENGLISH("en"), SPANISH("es") }

    private const val PREFS = "archdroid_language"
    private const val KEY = "tag"

    fun current(context: Context): Choice {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            storedTag(context)
        }
        return fromTag(tag)
    }

    fun apply(activity: Activity, choice: Choice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activity with the new configuration.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(choice.tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, choice.tag) }
            activity.recreate()
        }
    }

    /** Below Android 13 only. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base).ifEmpty { return base }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    fun fromTag(tag: String): Choice {
        val language = tag.substringBefore(',').substringBefore('-').lowercase()
        return Choice.entries.firstOrNull { it.tag.isNotEmpty() && it.tag == language } ?: Choice.SYSTEM
    }

    private fun storedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
}

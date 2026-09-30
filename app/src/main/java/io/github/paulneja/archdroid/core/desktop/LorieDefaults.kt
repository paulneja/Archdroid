package io.github.paulneja.archdroid.core.desktop

import android.content.Context
import androidx.core.content.edit

object LorieDefaults {
    private val DEFAULTS = mapOf(
        "fullscreen" to true,

        "touchMode" to "1",
        "screenIdleTimeout" to "never",
        "hideCutout" to true,
    )

    fun apply(context: Context) {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        prefs.edit {

            if (!prefs.getBoolean("archdroidFitDisplayV1", false)) {
                putString("displayResolutionMode", "native")
                putBoolean("fullscreen", true)
                putBoolean("hideCutout", true)
                putBoolean("archdroidFitDisplayV1", true)
            }
            DEFAULTS.forEach { (key, value) ->
                if (!prefs.contains(key)) when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                }
            }
        }
    }

    fun extraKeysShown(context: Context): Boolean =
        prefs(context).getBoolean(SHOW_KEYS, true) && prefs(context).getBoolean(KEYS_VISIBLE, true)

    fun setExtraKeysShown(context: Context, shown: Boolean) {
        prefs(context).edit {
            putBoolean(SHOW_KEYS, shown)
            if (shown) putBoolean(KEYS_VISIBLE, true)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    private const val SHOW_KEYS = "showAdditionalKbd"
    private const val KEYS_VISIBLE = "additionalKbdVisible"
}

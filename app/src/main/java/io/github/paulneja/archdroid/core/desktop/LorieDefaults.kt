package io.github.paulneja.archdroid.core.desktop

import android.content.Context
import androidx.core.content.edit

object LorieDefaults {
    private val DEFAULTS = mapOf(
        "fullscreen" to true,
        // Trackpad: the finger moves a visible cursor, like a laptop touchpad.
        "touchMode" to "1",
        "screenIdleTimeout" to "never",
        "hideCutout" to true,
    )

    fun apply(context: Context) {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        prefs.edit {
            // Adopt a display that follows rotation once, then preserve later user changes.
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

    /** The key bar (ESC, /, HOME, arrows...) under the desktop. */
    fun extraKeysShown(context: Context): Boolean =
        prefs(context).getBoolean(SHOW_KEYS, true) && prefs(context).getBoolean(KEYS_VISIBLE, true)

    /** Uses showAdditionalKbd: Termux:X11 only applies it live. */
    fun setExtraKeysShown(context: Context, shown: Boolean) {
        prefs(context).edit {
            putBoolean(SHOW_KEYS, shown)
            if (shown) putBoolean(KEYS_VISIBLE, true)
        }
    }

    // androidx.preference default file, used by Termux:X11.
    private fun prefs(context: Context) =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    private const val SHOW_KEYS = "showAdditionalKbd"
    private const val KEYS_VISIBLE = "additionalKbdVisible"
}

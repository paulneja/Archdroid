package io.github.paulneja.archdroid.ui

object Routes {
    /** Pager host for Home, Terminal, Desktop and Settings. */
    const val MAIN = "main"
    /** Only used as an external request: selects the Terminal page. */
    const val TERMINAL = "terminal"
    const val SETTINGS_MOUNTS = "settings/mounts"
    const val SETTINGS_ENV = "settings/env"
    const val SETTINGS_USER = "settings/user"
    const val SETTINGS_BEHAVIOR = "settings/behavior"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_PROTECTION = "settings/protection"
    const val SETTINGS_INIT = "settings/init"
    const val SETTINGS_ABOUT = "settings/about"
    const val INSTALL = "install"

    fun acceptsExternalRequest(route: String?): Boolean = route == TERMINAL || route == INSTALL
}

package io.github.paulneja.archdroid.core.terminal

/** Decides when the session service must hold the process in the foreground. */
object SessionSurvival {

    fun shouldRun(
        survivalEnabled: Boolean,
        sessions: Int,
        protectedSessions: Int,
        desktopRunning: Boolean = false,
    ): Boolean = protectedSessions > 0 || desktopRunning || (survivalEnabled && sessions > 0)

    /** Protected tabs are only closed after the user confirms. */
    fun needsCloseConfirmation(isProtected: Boolean): Boolean = isProtected
}

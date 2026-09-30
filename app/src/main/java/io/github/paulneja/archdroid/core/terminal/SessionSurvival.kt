package io.github.paulneja.archdroid.core.terminal

object SessionSurvival {

    fun shouldRun(
        survivalEnabled: Boolean,
        sessions: Int,
        protectedSessions: Int,
        desktopRunning: Boolean = false,
    ): Boolean = protectedSessions > 0 || desktopRunning || (survivalEnabled && sessions > 0)

    fun needsCloseConfirmation(isProtected: Boolean): Boolean = isProtected
}

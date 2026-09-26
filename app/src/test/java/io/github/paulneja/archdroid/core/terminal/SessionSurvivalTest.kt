package io.github.paulneja.archdroid.core.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSurvivalTest {

    @Test
    fun `runs while terminals are open and survival is on`() {
        assertTrue(SessionSurvival.shouldRun(survivalEnabled = true, sessions = 1, protectedSessions = 0))
    }

    @Test
    fun `stays off without sessions`() {
        assertFalse(SessionSurvival.shouldRun(survivalEnabled = true, sessions = 0, protectedSessions = 0))
    }

    @Test
    fun `a protected tab keeps it running even with survival off`() {
        assertTrue(SessionSurvival.shouldRun(survivalEnabled = false, sessions = 2, protectedSessions = 1))
    }

    @Test
    fun `a running desktop keeps it running`() {
        assertTrue(
            SessionSurvival.shouldRun(
                survivalEnabled = false, sessions = 0, protectedSessions = 0, desktopRunning = true,
            )
        )
    }

    @Test
    fun `survival off and nothing protected lets Android manage the process`() {
        assertFalse(SessionSurvival.shouldRun(survivalEnabled = false, sessions = 3, protectedSessions = 0))
    }

    @Test
    fun `only protected tabs ask before closing`() {
        assertTrue(SessionSurvival.needsCloseConfirmation(isProtected = true))
        assertFalse(SessionSurvival.needsCloseConfirmation(isProtected = false))
    }
}

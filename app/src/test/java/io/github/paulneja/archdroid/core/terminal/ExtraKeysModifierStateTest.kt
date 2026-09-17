package io.github.paulneja.archdroid.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraKeysModifierStateTest {

    private val state = ExtraKeysModifierState()

    @Test
    fun `starts with nothing held`() {
        assertFalse(state.ctrlActive)
        assertFalse(state.altActive)
        assertFalse(state.shiftActive)
    }

    @Test
    fun `toggling holds a modifier down`() {
        state.toggleCtrl()
        assertTrue(state.ctrlActive)
        state.toggleCtrl()
        assertFalse(state.ctrlActive)
    }

    @Test
    fun `modifiers are independent`() {
        state.toggleCtrl()
        state.toggleShift()
        assertTrue(state.ctrlActive)
        assertTrue(state.shiftActive)
        assertFalse(state.altActive)
    }

    @Test
    fun `a keypress releases every held modifier`() {
        // One-shot, like a physical keyboard: Ctrl then C sends Ctrl+C, and the C after
        // that is a plain C. Without this the modifier stays latched forever and every
        // following letter is mangled.
        state.toggleCtrl()
        state.toggleAlt()
        state.toggleShift()
        state.resetTransient()
        assertFalse(state.ctrlActive)
        assertFalse(state.altActive)
        assertFalse(state.shiftActive)
    }

    @Test
    fun `every change notifies the ui once`() {
        var changes = 0
        state.onChange = { changes++ }
        state.toggleCtrl()
        state.toggleAlt()
        assertEquals(2, changes)
        state.resetTransient()
        assertEquals(3, changes)
    }

    @Test
    fun `releasing when nothing is held does not churn the ui`() {
        var changes = 0
        state.onChange = { changes++ }
        state.resetTransient()
        assertEquals("no modifier was held, so nothing changed", 0, changes)
    }
}

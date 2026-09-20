package io.github.paulneja.archdroid.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTabStateTest {

    @Test
    fun `removing a background tab preserves the selected tab`() {
        assertEquals(0, TerminalTabState.afterRemoval(size = 3, activeIndex = 1, removedIndex = 0))
    }

    @Test
    fun `removing a tab after the selected tab preserves its index`() {
        assertEquals(0, TerminalTabState.afterRemoval(size = 3, activeIndex = 0, removedIndex = 2))
    }

    @Test
    fun `removing the active last tab selects the previous tab`() {
        assertEquals(0, TerminalTabState.afterRemoval(size = 2, activeIndex = 1, removedIndex = 1))
    }

    @Test
    fun `no tabs returns no active index`() {
        assertEquals(-1, TerminalTabState.afterRemoval(size = 1, activeIndex = 0, removedIndex = 0))
    }

    @Test
    fun `closing tab waits for both process exit and successful tree termination`() {
        assertEquals(false, TerminalTabState.canFinalizeClose(exitObserved = true, terminationSucceeded = false))
        assertEquals(false, TerminalTabState.canFinalizeClose(exitObserved = false, terminationSucceeded = true))
        assertEquals(true, TerminalTabState.canFinalizeClose(exitObserved = true, terminationSucceeded = true))
    }
}

package io.github.paulneja.archdroid.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionProcessTreeTest {

    @Test
    fun `selects only pids whose posix session matches the terminal leader`() {
        val stats = listOf(
            "101 (su) S 70 101 101 0",
            "115 (bash) S 101 115 101 0",
            "202 (su) S 70 202 202 0",
        )

        assertEquals(listOf(101, 115), SessionProcessTree.members(101, stats))
    }

    @Test
    fun `handles process names containing closing parentheses`() {
        val stats = listOf(
            "115 (odd) name)) S 101 115 101 0",
            "202 (other) S 70 202 202 0",
        )

        assertEquals(listOf(115), SessionProcessTree.members(101, stats))
    }

    @Test
    fun `rejects invalid leader pids`() {
        assertThrows(IllegalArgumentException::class.java) { SessionProcessTree.script(0) }
        assertThrows(IllegalArgumentException::class.java) { SessionProcessTree.script(-1) }
    }

    @Test
    fun `generated script is scoped to proc session membership`() {
        val script = SessionProcessTree.script(101)

        assertTrue(script.contains("/proc/[0-9]*/stat"))
        assertTrue(script.contains("kill -TERM"))
        assertTrue(script.contains("kill -KILL"))
        assertFalse(script.contains("pkill"))
        assertFalse(script.contains("/data/local/arch"))
    }
}

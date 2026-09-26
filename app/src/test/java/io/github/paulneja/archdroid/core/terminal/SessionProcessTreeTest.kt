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
        assertThrows(IllegalArgumentException::class.java) {
            SessionProcessTree.ProcessIdentity(0, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SessionProcessTree.ProcessIdentity(-1, -1, 1)
        }
    }

    @Test
    fun `identity read command uses the exact numeric proc path`() {
        assertEquals("cat /proc/26827/stat", SessionProcessTree.identityReadCommand(26_827))
        assertThrows(IllegalArgumentException::class.java) {
            SessionProcessTree.identityReadCommand(0)
        }
    }

    @Test
    fun `generated script is scoped to proc session membership`() {
        val script = SessionProcessTree.script(
            SessionProcessTree.ProcessIdentity(pid = 101, sessionId = 101, startTimeTicks = 9_001)
        )

        assertTrue(script.contains("/proc/[0-9]*/stat"))
        assertTrue(script.contains("EXPECTED_LEADER_START=9001"))
        assertTrue(script.contains("signal_one KILL"))
        assertFalse(script.contains("signal_one TERM"))
        assertFalse(script.contains("cat \""))
        assertFalse(script.contains("sleep "))
        assertFalse(script.contains("pkill"))
        assertFalse(script.contains("/data/local/arch"))
        assertFalse(script.contains("exit 0"))
    }

    @Test
    fun `oom script only touches verified members of the session`() {
        val script = SessionProcessTree.oomScript(
            SessionProcessTree.ProcessIdentity(pid = 101, sessionId = 101, startTimeTicks = 9_001),
            -1000,
        )
        assertTrue(script.contains("session_members"))
        assertTrue(script.contains("read_member"))
        assertTrue(script.contains("echo -1000 > /proc/"))
        assertFalse(script.contains("kill "))
    }

    @Test
    fun `process identity includes kernel start time and rejects a reused leader`() {
        val original = SessionProcessTree.identity(stat(pid = 101, name = "su", startTime = 9_001))
        val reused = stat(pid = 101, name = "unrelated", startTime = 9_999)

        assertEquals(9_001L, original.startTimeTicks)
        assertEquals(emptyList<Int>(), SessionProcessTree.members(original, listOf(reused)))
    }

    private fun stat(pid: Int, name: String, startTime: Long): String =
        "$pid ($name) S 70 $pid $pid " + List(15) { "0" }.joinToString(" ") + " $startTime"
}

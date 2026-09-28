package io.github.paulneja.archdroid.core.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionActivityTest {
    private val leader = SessionProcessTree.ProcessIdentity(101, 101, 9001)
    private fun stat(pid: Int, name: String, sid: Int = 101, state: String = "S", start: Long = 9001): String =
        "$pid ($name) $state 70 $pid $sid 34817 $pid 0 0 0 0 0 0 0 0 0 0 0 0 0 $start"
    private val idle = listOf(stat(101, "su"), stat(115, "bash"))

    @Test fun `idle login shell is not work`() {
        assertFalse(SessionActivity.hasWork(listOf(leader), idle))
    }
    @Test fun `foreground and background commands count as work`() {
        assertTrue(SessionActivity.hasWork(listOf(leader), idle + stat(120, "sleep")))
        assertTrue(SessionActivity.hasWork(listOf(leader), idle + stat(120, "odd) name)")))
    }
    @Test fun `unrelated processes and finished jobs are ignored`() {
        assertFalse(SessionActivity.hasWork(listOf(leader), idle + stat(120, "pacman", sid = 202)))
        assertFalse(SessionActivity.hasWork(listOf(leader), idle + stat(120, "sleep", state = "Z")))
    }
    @Test fun `a reused pid cannot report another sessions activity`() {
        assertFalse(SessionActivity.hasWork(listOf(leader), listOf(stat(101, "su", start = 9100), stat(120, "sleep"))))
        assertFalse(SessionActivity.hasWork(listOf(leader), listOf(stat(120, "sleep"))))
    }
}

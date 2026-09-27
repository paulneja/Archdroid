package io.github.paulneja.archdroid.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessShieldTest {

    @Test
    fun `the running shell moves itself so its children inherit the cgroup`() {
        val cmds = ProcessShield.escapeSelfCommands().joinToString("\n")
        assertTrue(cmds.contains("mkdir -p /sys/fs/cgroup/archdroid"))
        assertTrue(cmds.contains("read archdroid_pid _ < /proc/self/stat"))
        assertTrue(cmds.contains("echo \$archdroid_pid > /sys/fs/cgroup/archdroid/cgroup.procs"))
    }

    @Test
    fun `oom command writes the exact proc file`() {
        assertEquals("echo -1000 > /proc/42/oom_score_adj", ProcessShield.oomCommand(42, -1000))
    }

    @Test
    fun `rejects scores the kernel would refuse`() {
        assertThrows(IllegalArgumentException::class.java) { ProcessShield.oomCommand(42, -1001) }
        assertThrows(IllegalArgumentException::class.java) { ProcessShield.oomCommand(0, 0) }
    }
}

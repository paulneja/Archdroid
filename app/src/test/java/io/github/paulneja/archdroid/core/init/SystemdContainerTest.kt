package io.github.paulneja.archdroid.core.init

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemdContainerTest {

    private val c = "/data/local/arch"

    @Test
    fun `systemd gets its own pid, mount and cgroup namespaces`() {
        val cmd = SystemdContainer.bootCommand(c)
        assertTrue(cmd.contains("unshare --pid --fork --mount --uts --cgroup --mount-proc"))
        assertTrue(cmd.contains("container=archdroid"))
        assertTrue(cmd.contains("/usr/lib/systemd/systemd --system"))
    }

    @Test
    fun `pid 1 is found by its namespaced pid, not by name`() {
        val script = SystemdContainer.findPid1Script(c)
        assertTrue(script.contains("NSpid:"))
        assertTrue(script.contains("\"\${p}/root\" -ef '$c'"))
    }

    @Test
    fun `logins enter every namespace of pid 1`() {
        assertTrue(
            SystemdContainer.loginCommand(c, "123", "paul")
                .contains("nsenter --target 123 --all /bin/su - 'paul'")
        )
        assertTrue(SystemdContainer.loginCommand(c, "123", null).endsWith("/bin/bash -l"))
    }
}

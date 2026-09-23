package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacmanConfTest {

    private val cmds = PacmanConf.androidCompatCommands("/etc/pacman.conf").joinToString("\n")

    @Test
    fun `turns off the landlock filesystem sandbox`() {
        assertTrue(cmds.contains("DisableSandboxFilesystem"))
    }

    @Test
    fun `turns off the seccomp syscall sandbox`() {
        assertTrue(cmds.contains("DisableSandboxSyscalls"))
    }

    @Test
    fun `stops pacman dropping to the alpm download user`() {
        assertTrue(cmds.contains("DownloadUser"))
        assertTrue("DownloadUser must end up commented out", cmds.contains("#\\1"))
    }

    @Test
    fun `falls back to inserting under options when the line is absent`() {
        assertTrue(cmds.contains("""\[options\]"""))
    }

    @Test
    fun `turns off the free space check`() {
        assertTrue(cmds.contains("CheckSpace"))
    }

    @Test
    fun `leaves the kernel and firmware alone`() {
        assertTrue(cmds.contains("IgnorePkg"))
        assertTrue(cmds.contains("linux-aarch64"))
        assertTrue(cmds.contains("linux-firmware"))
    }

    @Test
    fun `sticks to sed forms toybox understands`() {
        assertFalse("s// is a GNU extension", cmds.contains("s//"))
    }

    @Test
    fun `every step is idempotent`() {
        PacmanConf.androidCompatCommands("/etc/pacman.conf").forEach { cmd ->
            val guarded = cmd.contains("grep -q") || cmd.startsWith("sed -i")
            assertTrue("not safe to run twice: $cmd", guarded)
        }
    }
}

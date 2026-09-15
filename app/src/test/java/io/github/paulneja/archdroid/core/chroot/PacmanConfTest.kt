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
        // A rootfs that never shipped the commented line still has to end up with the
        // option, and it has to land inside [options] -- appending would put it under a
        // repository section where pacman ignores it.
        // escaped for sed: \[options\]
        assertTrue(cmds.contains("""\[options\]"""))
    }

    @Test
    fun `turns off the free space check`() {
        // Inside a chroot /proc/mounts carries the host's paths, so pacman cannot map
        // /var/cache/pacman/pkg to a filesystem and refuses the transaction for lack of
        // space on a device with hundreds of gigabytes free.
        assertTrue(cmds.contains("CheckSpace"))
    }

    @Test
    fun `leaves the kernel and firmware alone`() {
        // A chroot boots nothing: linux-aarch64 is 370 MiB of kernel that can never run,
        // and upgrading it fires mkinitcpio, which fails and takes the install with it.
        assertTrue(cmds.contains("IgnorePkg"))
        assertTrue(cmds.contains("linux-aarch64"))
        assertTrue(cmds.contains("linux-firmware"))
    }

    @Test
    fun `sticks to sed forms toybox understands`() {
        // Android ships toybox sed, not GNU sed. An empty regex that reuses the previous
        // one -- s//replacement/ -- is a GNU extension; toybox answers "no previous regex"
        // and inserts nothing, silently leaving the option unset.
        assertFalse("s// is a GNU extension", cmds.contains("s//"))
    }

    @Test
    fun `every step is idempotent`() {
        // Re-running an install must not double up lines.
        PacmanConf.androidCompatCommands("/etc/pacman.conf").forEach { cmd ->
            val guarded = cmd.contains("grep -q") || cmd.startsWith("sed -i")
            assertTrue("not safe to run twice: $cmd", guarded)
        }
    }
}

package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.data.model.DevMode
import io.github.paulneja.archdroid.data.model.MountConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevModeTest {

    private val c = "/data/local/arch"

    private fun cmds(mode: DevMode) =
        MountPlan.mountCommands(MountConfig(devMode = mode), c)

    @Test
    fun `the safe private dev is the default`() {
        assertEquals(DevMode.PRIVATE, MountConfig().devMode)
    }

    @Test
    fun `private dev never binds the real dev`() {
        val joined = cmds(DevMode.PRIVATE).joinToString("\n")
        assertFalse(
            "binding the host /dev is the path that lets a delete escape the chroot",
            joined.contains("mount --bind /dev"),
        )
        assertTrue(joined.contains("mount -t tmpfs"))
        assertTrue(joined.contains("$c/dev"))
    }

    @Test
    fun `private dev creates the device nodes a shell needs`() {
        val joined = cmds(DevMode.PRIVATE).joinToString("\n")
        assertTrue(joined.contains("mknod -m 666 $c/dev/null c 1 3"))
        assertTrue(joined.contains("mknod -m 666 $c/dev/zero c 1 5"))
        assertTrue(joined.contains("mknod -m 666 $c/dev/full c 1 7"))
        assertTrue(joined.contains("mknod -m 666 $c/dev/random c 1 8"))
        assertTrue(joined.contains("mknod -m 666 $c/dev/urandom c 1 9"))
        assertTrue(joined.contains("mknod -m 666 $c/dev/tty c 5 0"))
    }

    @Test
    fun `private dev makes the nodes only after mounting the tmpfs over it`() {
        val list = cmds(DevMode.PRIVATE)
        val mount = list.indexOfFirst { it.contains("mount -t tmpfs") && it.contains("$c/dev") }
        val firstNode = list.indexOfFirst { it.contains("mknod") }
        assertTrue("tmpfs mount not found", mount >= 0)
        assertTrue("nodes must be created after the tmpfs is mounted", firstNode > mount)
    }

    @Test
    fun `private dev carries the file descriptor links scripts expect`() {
        // mkinitcpio tests for /dev/fd to decide whether /dev is mounted at all, and
        // shell redirections lean on /dev/stdin and friends. A bare tmpfs has none.
        val joined = cmds(DevMode.PRIVATE).joinToString("\n")
        assertTrue(joined.contains("ln -sf /proc/self/fd $c/dev/fd"))
        assertTrue(joined.contains("ln -sf /proc/self/fd/0 $c/dev/stdin"))
        assertTrue(joined.contains("ln -sf /proc/self/fd/1 $c/dev/stdout"))
        assertTrue(joined.contains("ln -sf /proc/self/fd/2 $c/dev/stderr"))
    }

    @Test
    fun `private dev points ptmx at the devpts instance`() {
        assertTrue(cmds(DevMode.PRIVATE).joinToString("\n").contains("ln -s pts/ptmx $c/dev/ptmx"))
    }

    @Test
    fun `bind mode still binds the host dev for maximum compatibility`() {
        val joined = cmds(DevMode.BIND).joinToString("\n")
        assertTrue(joined.contains("mount --bind /dev $c/dev"))
        assertFalse("bind mode must not create nodes of its own", joined.contains("mknod"))
    }

    @Test
    fun `both modes create the dev subdirectories after mounting over dev`() {
        for (mode in DevMode.entries) {
            val list = cmds(mode)
            // endsWith, so the shm mount further down does not match as well
            val over = list.indexOfLast { it.endsWith("$c/dev") }
            val mkdirShm = list.indexOfLast { it.contains("mkdir") && it.contains("$c/dev/shm") }
            assertTrue("$mode: nothing mounted over dev", over >= 0)
            assertTrue("$mode: dev/shm must be created after that mount", mkdirShm > over)
        }
    }
}

package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.data.model.MountConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MountPlanTest {

    private val c = "/data/local/arch"

    @Test
    fun `mounts the virtual filesystems that are enabled`() {
        val cmds = MountPlan.mountCommands(MountConfig(), c).joinToString("\n")
        assertTrue(cmds.contains("mount -t proc proc $c/proc"))
        assertTrue(cmds.contains("mount -t sysfs sysfs $c/sys"))
        assertTrue(cmds.contains("mount --bind /dev $c/dev"))
        assertTrue(cmds.contains("mount -t devpts devpts $c/dev/pts"))
        assertTrue(cmds.contains("size=256M"))
    }

    @Test
    fun `skips the filesystems that are disabled`() {
        val cfg = MountConfig(proc = false, sys = false, shm = false)
        val cmds = MountPlan.mountCommands(cfg, c).joinToString("\n")
        assertFalse(cmds.contains("mount -t proc"))
        assertFalse(cmds.contains("mount -t sysfs"))
        assertFalse(cmds.contains("tmpfs"))
    }

    @Test
    fun `quotes the shared storage paths`() {
        val cfg = MountConfig(
            bindSharedStorage = true,
            sharedStorageSource = "/sdcard/my stuff",
            sharedStorageTarget = "/mnt/it's",
        )
        val cmds = MountPlan.mountCommands(cfg, c).joinToString("\n")
        assertTrue(cmds.contains("""'/sdcard/my stuff'"""))
        assertTrue(cmds.contains("""'$c/mnt/it'\''s'"""))
    }

    @Test
    fun `unmounts deepest paths first`() {
        val cfg = MountConfig(bindSharedStorage = true)
        val cmds = MountPlan.unmountCommands(cfg, c)
        val shm = cmds.indexOfFirst { it.contains("$c/dev/shm") }
        val dev = cmds.indexOfFirst { it.contains("umount '$c/dev'") }
        assertTrue("shm must be unmounted before dev", shm in 0 until dev)
    }

    @Test
    fun `residual mounts finds a mount inside the chroot`() {
        val procMounts = listOf(
            "/dev/block/dm-9 /data ext4 rw 0 0",
            "proc /data/local/arch/proc proc rw 0 0",
            "/dev/block/dm-9 /data/local/arch/mnt/sdcard ext4 rw 0 0",
        )
        assertEquals(
            listOf("/data/local/arch/proc", "/data/local/arch/mnt/sdcard"),
            MountPlan.residualMounts(procMounts, c),
        )
    }

    @Test
    fun `residual mounts ignores a sibling directory with a shared prefix`() {
        val procMounts = listOf("/dev/block/dm-9 /data/local/archive ext4 rw 0 0")
        assertTrue(MountPlan.residualMounts(procMounts, c).isEmpty())
    }

    @Test
    fun `residual mounts counts the chroot root itself`() {
        val procMounts = listOf("/dev/block/loop0 /data/local/arch ext4 rw 0 0")
        assertEquals(listOf(c), MountPlan.residualMounts(procMounts, c))
    }

    @Test
    fun `residual mounts decodes octal escaped spaces`() {
        val procMounts = listOf("none /data/local/arch/mnt/my\\040files tmpfs rw 0 0")
        assertEquals(listOf("/data/local/arch/mnt/my files"), MountPlan.residualMounts(procMounts, c))
    }

    @Test
    fun `residual mounts ignores blank and malformed lines`() {
        val procMounts = listOf("", "   ", "garbage")
        assertTrue(MountPlan.residualMounts(procMounts, c).isEmpty())
    }
}

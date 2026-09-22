package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.data.model.DevMode
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
        assertTrue("something must be mounted at $c/dev", cmds.contains("tmpfs $c/dev"))
        assertTrue(cmds.contains("mount -t devpts devpts $c/dev/pts"))
        assertTrue(cmds.contains("size=256M"))
    }

    @Test
    fun `skips the filesystems that are disabled`() {
        val cfg = MountConfig(proc = false, sys = false, shm = false)
        val cmds = MountPlan.mountCommands(cfg, c).joinToString("\n")
        assertFalse(cmds.contains("mount -t proc"))
        assertFalse(cmds.contains("mount -t sysfs"))
        assertFalse("the shm tmpfs must be gone", cmds.contains("$c/dev/shm"))
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
    fun `creates the dev subdirectories after binding dev over them`() {
        // mkdir before the bind is useless: the bind hides whatever was created, and the
        // devpts/shm mount points then resolve inside Android's own /dev.
        val cmds = MountPlan.mountCommands(MountConfig(devMode = DevMode.BIND), c)
        val bind = cmds.indexOfFirst { it.contains("mount --bind /dev") }
        val mkdirPts = cmds.indexOfLast { it.contains("mkdir") && it.contains("$c/dev/pts") }
        val mkdirShm = cmds.indexOfLast { it.contains("mkdir") && it.contains("$c/dev/shm") }

        assertTrue("no dev bind found", bind >= 0)
        assertTrue("dev/pts must be created after the bind", mkdirPts > bind)
        assertTrue("dev/shm must be created after the bind", mkdirShm > bind)
    }

    @Test
    fun `mounts each dev subdirectory only after creating it`() {
        val cmds = MountPlan.mountCommands(MountConfig(devMode = DevMode.BIND), c)
        val mkdirShm = cmds.indexOfLast { it.contains("mkdir") && it.contains("$c/dev/shm") }
        val mountShm = cmds.indexOfFirst { it.contains("mount -t tmpfs") && it.contains("$c/dev/shm") }
        assertTrue("shm must be created before it is mounted", mkdirShm < mountShm)
    }

    @Test
    fun `expected mount points follow the config`() {
        assertEquals(
            listOf(c, "$c/proc", "$c/sys", "$c/dev", "$c/dev/pts", "$c/dev/shm"),
            MountPlan.expectedMountPoints(MountConfig(), c),
        )
        assertEquals(
            listOf(c, "$c/proc"),
            MountPlan.expectedMountPoints(
                MountConfig(sys = false, dev = false, devPts = false, shm = false), c,
            ),
        )
    }

    @Test
    fun `expected mount points include the shared storage bind`() {
        val cfg = MountConfig(bindSharedStorage = true, sharedStorageTarget = "/mnt/sd")
        assertTrue(MountPlan.expectedMountPoints(cfg, c).contains("$c/mnt/sd"))
    }

    @Test
    fun `missing mounts names exactly what did not come up`() {
        val procMounts = listOf(
            "/dev/block/dm-9 /data/local/arch ext4 rw 0 0",
            "proc /data/local/arch/proc proc rw 0 0",
            "sysfs /data/local/arch/sys sysfs rw 0 0",
            "tmpfs /data/local/arch/dev tmpfs rw 0 0",
            "devpts /data/local/arch/dev/pts devpts rw 0 0",
        )
        assertEquals(
            listOf("$c/dev/shm"),
            MountPlan.missingMounts(procMounts, MountConfig(), c),
        )
    }

    @Test
    fun `missing mounts is empty when everything is up`() {
        val procMounts = MountPlan.expectedMountPoints(MountConfig(), c)
            .map { "src $it type rw 0 0" }
        assertTrue(MountPlan.missingMounts(procMounts, MountConfig(), c).isEmpty())
    }

    @Test
    fun `binds the chroot onto itself before anything else`() {
        // /data is nosuid, which leaves sudo unable to elevate. A self-bind gives the
        // chroot its own mount that can carry suid, and it must be first because it lands
        // on top of the directory and would hide mounts made before it.
        val cmds = MountPlan.mountCommands(MountConfig(), c)
        assertTrue(cmds[0].contains("mount --bind $c $c"))
        assertTrue(cmds[1].contains("remount,suid,exec,dev $c"))
    }

    @Test
    fun `the self bind is expected and unmounted last`() {
        assertTrue(MountPlan.expectedMountPoints(MountConfig(), c).contains(c))
        val un = MountPlan.unmountCommands(MountConfig(), c)
        assertTrue("the outermost mount comes off last", un.last().contains("'$c'"))
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
    fun `unmount drains stacked mounts at each path`() {
        val commands = MountPlan.unmountCommands(MountConfig(), c)

        assertTrue(commands.all { it.contains("while [ \$attempt -lt 16 ]") })
        assertTrue(commands.all { it.contains("attempt=\$((attempt + 1))") })
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

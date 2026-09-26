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
        assertTrue("android's dev must be bound at $c/dev", cmds.contains("mount --bind /dev $c/dev"))
        assertTrue(cmds.contains("mount --bind /dev/pts $c/dev/pts"))
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
            sharedStorageSource = "/sdcard/it's mine",
            sharedStorageTarget = "/mnt/sd",
        )
        val cmds = MountPlan.mountCommands(cfg, c).joinToString("\n")
        assertTrue(cmds.contains("""'/sdcard/it'\''s mine'"""))
        assertTrue(cmds.contains("""grep -qF ' $c/mnt/sd ' /proc/mounts"""))
    }

    @Test
    fun `read only shared storage is remounted without hiding failures`() {
        val cfg = MountConfig(bindSharedStorage = true, sharedStorageReadOnly = true)
        val remount = MountPlan.mountCommands(cfg, c).single { it.contains("remount,ro,bind") }
        assertFalse(remount.contains("|| true"))
    }

    @Test
    fun `a writable shared storage mount is switched back to rw`() {
        val cfg = MountConfig(bindSharedStorage = true, sharedStorageReadOnly = false)
        assertTrue(MountPlan.mountCommands(cfg, c).any { it.contains("remount,rw,bind") })
    }

    @Test
    fun `a shared mount left writable counts as missing when read only was asked`() {
        val cfg = MountConfig(bindSharedStorage = true, sharedStorageReadOnly = true)
        val procMounts = MountPlan.expectedMountPoints(cfg, c).map { "src $it type rw 0 0" }
        assertEquals(listOf("$c/mnt/sdcard"), MountPlan.missingMounts(procMounts, cfg, c))
        val fixed = procMounts + "src $c/mnt/sdcard type ro,nosuid 0 0"
        assertTrue(MountPlan.missingMounts(fixed, cfg, c).isEmpty())
    }

    @Test
    fun `validation accepts the defaults`() {
        assertTrue(MountPlan.validate(MountConfig(bindSharedStorage = true)).isEmpty())
    }

    @Test
    fun `validation rejects targets that escape or break mount matching`() {
        listOf("../../system", "/mnt/../../system", "/", "/mnt/sd/", "/mnt//sd", "/mnt/my sd",
            "/mnt/\$(reboot)", "/mnt/sd\"x", "/proc", "/dev/block", "/etc", "relative",
        ).forEach { target ->
            val cfg = MountConfig(bindSharedStorage = true, sharedStorageTarget = target)
            assertTrue("accepted $target", MountPlan.validate(cfg).isNotEmpty())
        }
    }

    @Test
    fun `validation rejects relative or traversing sources`() {
        listOf("sdcard", "/sdcard/../data").forEach { source ->
            val cfg = MountConfig(bindSharedStorage = true, sharedStorageSource = source)
            assertTrue("accepted $source", MountPlan.validate(cfg).isNotEmpty())
        }
    }

    @Test
    fun `validation ignores shared paths while sharing is off`() {
        assertTrue(MountPlan.validate(MountConfig(sharedStorageTarget = "../x")).isEmpty())
    }

    @Test
    fun `creates the dev subdirectories after binding dev over them`() {
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
            listOf(c, "$c/run", "$c/proc", "$c/sys", "$c/dev", "$c/dev/pts", "$c/dev/shm"),
            MountPlan.expectedMountPoints(MountConfig(), c),
        )
        assertEquals(
            listOf(c, "$c/proc"),
            MountPlan.expectedMountPoints(
                MountConfig(run = false, sys = false, dev = false, devPts = false, shm = false), c,
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
            "tmpfs /data/local/arch/run tmpfs rw 0 0",
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
        val cmds = MountPlan.mountCommands(MountConfig(), c)
        assertTrue(cmds[0].contains("mount --bind $c $c"))
        assertTrue(cmds[1].contains("remount,suid,exec,dev $c"))
    }

    private val liveMounts = listOf(
        "/dev/block/dm-9 $c ext4 rw 0 0",
        "proc $c/proc proc rw 0 0",
        "tmpfs $c/dev tmpfs rw 0 0",
        "devpts $c/dev/pts devpts rw 0 0",
        "tmpfs $c/dev/shm tmpfs rw 0 0",
        "/dev/fuse $c/mnt/old fuse rw 0 0",
        "/dev/block/dm-9 /data ext4 rw 0 0",
    )

    @Test
    fun `the self bind is expected and unmounted last`() {
        assertTrue(MountPlan.expectedMountPoints(MountConfig(), c).contains(c))
        val un = MountPlan.unmountCommands(liveMounts, c)
        assertTrue("the outermost mount comes off last", un.last().contains("umount '$c' "))
    }

    @Test
    fun `unmounts deepest paths first`() {
        val cmds = MountPlan.unmountCommands(liveMounts, c)
        val shm = cmds.indexOfFirst { it.contains("$c/dev/shm") }
        val dev = cmds.indexOfFirst { it.contains("umount '$c/dev'") }
        assertTrue("shm must be unmounted before dev", shm in 0 until dev)
    }

    @Test
    fun `unmounts what is mounted even when the config no longer mentions it`() {
        val cmds = MountPlan.unmountCommands(liveMounts, c)
        assertTrue(cmds.any { it.contains("'$c/mnt/old'") })
        assertTrue(cmds.none { it.contains("umount '/data'") })
    }

    @Test
    fun `stacked mounts on one point are each unmounted`() {
        val stacked = listOf("a $c/mnt/sd x rw 0 0", "b $c/mnt/sd x rw 0 0")
        assertEquals(2, MountPlan.unmountCommands(stacked, c).size)
    }

    @Test
    fun `run is a tmpfs like systemd expects`() {
        val cmds = MountPlan.mountCommands(MountConfig(), c).joinToString("\n")
        assertTrue(cmds.contains("mount -t tmpfs -o mode=755,nosuid,nodev,size=128M tmpfs $c/run"))
    }

    @Test
    fun `stale mounts are the live ones the config does not want`() {
        val stale = MountPlan.staleMountLines(liveMounts, MountConfig(run = false), c)
        assertEquals(listOf("/dev/fuse $c/mnt/old fuse rw 0 0"), stale)
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

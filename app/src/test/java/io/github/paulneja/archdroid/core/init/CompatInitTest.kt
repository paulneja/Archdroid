package io.github.paulneja.archdroid.core.init

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompatInitTest {

    private val c = "/data/local/arch"

    private val assetDir: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets/init") }
            .first { it.isDirectory }

    @Test
    fun `the bundled systemctl never signals PID 1, which is Android's init`() {
        val script = File(assetDir, "systemctl3.py").readText()
        assertFalse(script.contains("os.kill(1,"))
        assertTrue(script.contains("ArchDroid modification notice"))
    }

    @Test
    fun `the replacement shadows the real systemctl through usr local bin`() {
        val cmds = CompatInit.installCommands(c, "/data/user/0/app/files/init").joinToString("\n")
        assertTrue(cmds.contains("ln -sf systemctl3.py '$c/usr/local/bin'/systemctl"))
        assertTrue(cmds.contains("ln -sf journalctl3.py '$c/usr/local/bin'/journalctl"))
    }

    @Test
    fun `units that would reconfigure the phone are masked`() {
        val cmds = CompatInit.maskCommands(c).joinToString("\n")
        listOf("systemd-networkd.service", "dhcpcd.service", "nftables.service", "iptables.service",
            "systemd-timesyncd.service", "wpa_supplicant.service").forEach { unit ->
            assertTrue("$unit is not masked", cmds.contains("/etc/systemd/system/$unit'"))
        }
    }

    @Test
    fun `masking never overwrites a unit file the user wrote`() {
        val one = CompatInit.maskCommands(c).first { it.contains("sshd") || it.contains("dhcpcd.service") }
        assertTrue(one.startsWith("[ -e ") && one.contains("[ ! -L ") && one.contains("|| ln -sf /dev/null"))
    }

    @Test
    fun `boot runs once per mount and starts enabled services`() {
        val boot = CompatInit.bootScript()
        assertTrue(boot.contains("[ -e /run/archdroid-booted ] && exit 0"))
        assertTrue(boot.contains("systemd-tmpfiles --create --boot"))
        assertTrue(boot.contains("--exclude-prefix=/dev"))
        assertTrue(boot.contains("dbus-daemon --system --fork"))
        assertTrue(boot.contains("/usr/local/bin/systemctl default"))
    }

    @Test
    fun `boot sets the rootfs PATH before running any command`() {
        // chroot keeps Android's PATH, where mkdir, dbus-daemon and python3 do not exist.
        val lines = CompatInit.bootScript().lines()
        val path = lines.indexOfFirst { it.startsWith("export PATH=/usr/local/sbin:/usr/local/bin:/usr/bin") }
        val firstCommand = lines.indexOfFirst { it.startsWith("mkdir") }
        assertTrue(path in 0 until firstCommand)
    }

    @Test
    fun `halt runs systemctl with the rootfs PATH so its python shebang resolves`() {
        assertTrue(CompatInit.haltCommand(c).contains("/usr/bin/env PATH=/usr/local/sbin:/usr/local/bin:/usr/bin"))
    }

    @Test
    fun `boot lifts the tmpfiles nologin gate like systemd-user-sessions does`() {
        // tmpfiles --boot creates /run/nologin; while it exists PAM refuses every non-root
        // login, including unlocking the desktop's screen locker.
        val boot = CompatInit.bootScript()
        assertTrue(boot.indexOf("rm -f /run/nologin") > boot.indexOf("systemd-tmpfiles --create --boot"))
    }

    @Test
    fun `services boot outside the app cgroup`() {
        val cmd = CompatInit.bootCommand(c)
        assertTrue(cmd.contains("/sys/fs/cgroup/archdroid/cgroup.procs"))
        assertTrue(cmd.indexOf("cgroup.procs") < cmd.indexOf("exec chroot"))
    }

    @Test
    fun `only processes rooted in the chroot are killed`() {
        val script = CompatInit.killChrootProcessesScript(c)
        assertTrue(script.contains("readlink \"\${p}/root\""))
        assertTrue(script.contains("case \"\${r}\" in '$c'|'$c'/*)"))
        assertFalse(script.contains("kill -KILL 1"))
    }
}

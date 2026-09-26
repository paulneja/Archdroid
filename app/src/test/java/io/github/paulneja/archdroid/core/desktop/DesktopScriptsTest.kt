package io.github.paulneja.archdroid.core.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopScriptsTest {

    private val c = "/data/local/arch"

    @Test
    fun `installs common plumbing once plus each desktop`() {
        val pkgs = DesktopScripts.installPackages(setOf(DesktopEnvironment.KDE, DesktopEnvironment.XFCE), true)
        assertTrue(pkgs.containsAll(listOf("xkeyboard-config", "pipewire", "plasma-desktop", "xfce4", "kwin")))
        assertTrue("no duplicates", pkgs.size == pkgs.toSet().size)
    }

    @Test
    fun `wayland packages are optional`() {
        val pkgs = DesktopScripts.installPackages(setOf(DesktopEnvironment.KDE), false)
        assertFalse(pkgs.contains("xorg-xwayland"))
    }

    @Test
    fun `plasma skips the systemd user manager in compat mode`() {
        assertTrue(DesktopScripts.configureUserCommand("paul").contains("systemdBoot=false"))
        assertTrue(DesktopScripts.configureUserCommand("root").contains("/root/.config/startkderc"))
    }

    @Test
    fun `the X server comes from the APK, runs in the chroot tmp and leaves the app cgroup`() {
        val script = DesktopScripts.startX11Server(c, "/data/app/x/base.apk")
        assertTrue(script.contains("export CLASSPATH='/data/app/x/base.apk'"))
        assertTrue(script.contains("TMPDIR=\"\${C}/tmp\""))
        assertTrue(script.contains("com.termux.x11.CmdEntryPoint :0"))
        assertTrue(script.indexOf("cgroup.procs") < script.indexOf("app_process"))
    }

    @Test
    fun `a dead server's socket and lock are cleared before starting a new one`() {
        val script = DesktopScripts.startX11Server(c, "/a.apk")
        val cleanup = script.indexOf("rm -f \"\${C}/tmp/.X11-unix/X0\" \"\${C}/tmp/.X0-lock\"")
        assertTrue(cleanup >= 0)
        assertTrue(cleanup < script.indexOf("app_process"))
    }

    @Test
    fun `a display is ready only when its socket is listening, not when the file exists`() {
        val probe = DesktopScripts.displayReady(c, ":0")
        assertTrue(probe.contains("/proc/net/unix"))
        assertTrue(probe.contains("$c/tmp/.X11-unix/X0"))
        assertFalse(probe.contains("[ -S"))
    }

    @Test
    fun `the vnc display gets the same stale cleanup`() {
        assertTrue(DesktopScripts.startVncServer(c, "paul", 10, 10).contains(".X1-lock"))
    }

    @Test
    fun `absolute xkb symlinks are resolved inside the rootfs`() {
        val script = DesktopScripts.startX11Server(c, "/a.apk")
        assertTrue(script.contains("readlink \"\${C}/usr/share/X11/xkb\""))
        assertTrue(script.contains("/*) XKB=\"\${C}\${L}\""))
    }

    @Test
    fun `the unauthenticated VNC server only listens on localhost`() {
        val cmd = DesktopScripts.startVncServer(c, "paul", 2400, 1080)
        assertTrue(cmd.contains("-SecurityTypes None"))
        assertTrue(cmd.contains("-localhost"))
        assertTrue(cmd.contains("-geometry 2400x1080"))
    }

    @Test
    fun `sessions run as the user, detached and outside the app cgroup`() {
        val cmd = DesktopScripts.startSession(c, "paul", DesktopEnvironment.KDE, SessionType.WAYLAND, ":0")
        assertTrue(cmd.contains("/bin/su - '\\''paul'\\'' -c"))
        assertTrue(cmd.contains("desktop-start kde wayland :0"))
        assertTrue(cmd.endsWith("&"))
    }

    @Test
    fun `x11 only desktops never start a wayland session`() {
        val cmd = DesktopScripts.startSession(c, "paul", DesktopEnvironment.XFCE, SessionType.WAYLAND, ":0")
        assertTrue(cmd.contains("desktop-start xfce x11 :0"))
    }

    @Test
    fun `the launcher knows every desktop and streams audio to the app`() {
        val script = DesktopScripts.startScript()
        listOf("startplasma-x11", "startplasma-wayland", "startxfce4", "startlxqt", "startlxqtwayland", "mate-session")
            .forEach { assertTrue(it, script.contains(it)) }
        assertTrue(script.contains("module-simple-protocol-tcp"))
        assertTrue(script.contains("listen=127.0.0.1"))
        assertTrue(script.contains("LIBGL_ALWAYS_SOFTWARE=1"))
        assertTrue(script.contains("exec >\"/tmp/archdroid-desktop-"))
    }

    @Test
    fun `stopping a session only signals the user's processes in the chroot`() {
        val script = DesktopScripts.stopSession(c, "paul")
        assertTrue(script.contains("\"\${p}/root\" -ef '$c'"))
        assertTrue(script.contains("stat -c %u"))
    }

    @Test
    fun `a running session is found from the server and the session processes`() {
        val script = DesktopScripts.detectSessionScript(c)
        assertTrue(script.contains("pgrep -f '^archdroid-x11'"))
        assertTrue(script.contains("Xvnc"))
        listOf("xfce4-session", "ksmserver", "lxqt-session", "mate-session").forEach {
            assertTrue(it, script.contains(it))
        }
    }

    @Test
    fun `detection output maps to an engine only when a session is alive`() {
        assertEquals(DisplayEngine.X11_NATIVE, DesktopScripts.parseSession(listOf("x11", "session")))
        assertEquals(DisplayEngine.VNC, DesktopScripts.parseSession(listOf("vnc", "session")))
        assertEquals(null, DesktopScripts.parseSession(listOf("x11")))
        assertEquals(null, DesktopScripts.parseSession(listOf("session")))
    }
}

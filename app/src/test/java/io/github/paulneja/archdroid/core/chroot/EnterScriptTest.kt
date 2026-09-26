package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.data.model.AppSettings
import io.github.paulneja.archdroid.data.model.BehaviorConfig
import io.github.paulneja.archdroid.data.model.EnvConfig
import io.github.paulneja.archdroid.data.model.InitMode
import io.github.paulneja.archdroid.data.model.UserConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterScriptTest {

    @Test
    fun `mounts everything itself before entering`() {
        val script = EnterScript.build(AppSettings())
        val bind = script.indexOf("mount --bind /data/local/arch /data/local/arch")
        val suid = script.indexOf("remount,suid")
        val enter = script.indexOf("exec chroot")
        assertTrue("the self bind must be in the script", bind >= 0)
        assertTrue("suid remount must be in the script", suid > bind)
        assertTrue("mounts come before entering", suid < enter)
    }

    @Test
    fun `leaves the app cgroup before running anything else`() {
        val lines = EnterScript.build(AppSettings()).lines()
        val escape = lines.indexOfFirst { it.contains("/sys/fs/cgroup/archdroid/cgroup.procs") }
        val firstMount = lines.indexOfFirst { it.contains("mount ") }
        assertTrue("escape must exist", escape > 0)
        assertTrue("escape must come before the first mount", escape < firstMount)
    }

    @Test
    fun `systemd mode logs in through pid 1 and keeps chroot as fallback`() {
        val script = EnterScript.build(
            AppSettings(user = UserConfig(username = "paul"), initMode = InitMode.SYSTEMD)
        )
        val nsenter = script.indexOf("nsenter --target")
        val fallback = script.indexOf("exec chroot /data/local/arch /bin/su - 'paul'")
        assertTrue(nsenter in 0 until fallback)
    }

    @Test
    fun `compat mode enters with plain chroot`() {
        assertFalse(EnterScript.build(AppSettings()).contains("nsenter"))
    }

    @Test
    fun `logs in as the configured user by default`() {
        val script = EnterScript.build(AppSettings(user = UserConfig(username = "paul")))
        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertTrue(script.contains("exec chroot /data/local/arch /bin/su - 'paul'"))
    }

    @Test
    fun `logs in as root when asked`() {
        val script = EnterScript.build(AppSettings(user = UserConfig(loginAsRoot = true)))
        assertTrue(script.contains("exec chroot /data/local/arch /bin/bash -l"))
        assertFalse(script.contains("/bin/su -"))
    }

    @Test
    fun `exports the terminal environment`() {
        val script = EnterScript.build(AppSettings())
        assertTrue(script.contains("export TERM=xterm-256color"))
        assertTrue(script.contains("export COLORTERM=truecolor"))
        assertTrue(script.contains("export LANG='en_US.UTF-8'"))
    }

    @Test
    fun `quotes extra env values`() {
        val settings = AppSettings(env = EnvConfig(extraEnv = mapOf("MSG" to "it's here")))
        val script = EnterScript.build(settings)
        assertTrue(script.contains("""export MSG='it'\''s here'"""))
    }

    @Test
    fun `drops env names that are not valid shell identifiers`() {
        val settings = AppSettings(
            env = EnvConfig(extraEnv = mapOf("BAD NAME" to "x", "X=Y" to "z", "OK_1" to "v")),
        )
        val script = EnterScript.build(settings)
        assertTrue(script.contains("export OK_1='v'"))
        assertFalse(script.contains("BAD NAME"))
        assertFalse(script.contains("X=Y"))
    }

    @Test
    fun `runs the startup script inside the chroot when configured`() {
        val settings = AppSettings(behavior = BehaviorConfig(startupScript = "/opt/go.sh"))
        val script = EnterScript.build(settings)
        assertTrue(script.contains("'/opt/go.sh'"))
    }

    @Test
    fun `omits the startup hook when no script is set`() {
        assertFalse(EnterScript.build(AppSettings()).contains("startup"))
    }
}

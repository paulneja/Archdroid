package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote
import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.model.AppSettings

/**
 * Builds the script the terminal executes as root: it sets up the session environment,
 * runs the user's startup hook inside the chroot, and hands the pty over to the shell.
 */
object EnterScript {

    fun build(cfg: AppSettings): String = buildString {
        val c = ChrootPaths.CHROOT_DIR
        appendLine("#!/system/bin/sh")
        // The mounts the app makes through libsu live in that shell's mount namespace,
        // and the terminal is a separate su process that may not share it -- the chroot
        // still enters, but without the suid bind sudo cannot elevate. Mounting from here
        // puts them in the same namespace as the session that is about to use them.
        MountPlan.mountCommands(cfg.mounts, c).forEach { appendLine(it) }
        appendLine("export TERM=xterm-256color")
        appendLine("export COLORTERM=truecolor")
        appendLine("export LANG=${quote(cfg.env.lang)}")
        cfg.env.extraEnv
            .filterKeys { ShellQuote.isValidEnvName(it) }
            .forEach { (k, v) -> appendLine("export $k=${quote(v)}") }

        val startup = cfg.behavior.startupScript.trim()
        if (startup.isNotEmpty()) {
            val inner = "test -f ${quote(startup)} && sh ${quote(startup)}"
            appendLine("chroot $c /bin/bash -lc ${quote(inner)} 2>/dev/null")
        }

        if (cfg.user.loginAsRoot) {
            appendLine("exec chroot $c /bin/bash -l")
        } else {
            appendLine("exec chroot $c /bin/su - ${quote(cfg.user.username)}")
        }
    }
}

package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.init.SystemdContainer
import io.github.paulneja.archdroid.core.terminal.ProcessShield
import io.github.paulneja.archdroid.data.model.InitMode
import io.github.paulneja.archdroid.core.util.ShellQuote
import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.model.AppSettings

object EnterScript {

    fun build(cfg: AppSettings): String = buildString {
        val c = ChrootPaths.CHROOT_DIR
        appendLine("#!/system/bin/sh")
        // Leave the app's cgroup before anything else starts, so every child inherits it.
        ProcessShield.escapeSelfCommands().forEach { appendLine(it) }
        // Mount in the terminal process namespace so sudo keeps its suid bind.
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

        if (cfg.initMode == InitMode.SYSTEMD) {
            appendLine("PID1=$(")
            appendLine(SystemdContainer.findPid1Script(c))
            appendLine(")")
            appendLine("if [ -n \"${'$'}PID1\" ]; then")
            appendLine(
                "  " + SystemdContainer.loginCommand(
                    c, "\"${'$'}PID1\"", cfg.user.username.takeUnless { cfg.user.loginAsRoot },
                )
            )
            appendLine("fi")
        }

        if (cfg.user.loginAsRoot) {
            appendLine("exec chroot $c /bin/bash -l")
        } else {
            appendLine("exec chroot $c /bin/su - ${quote(cfg.user.username)}")
        }
    }
}

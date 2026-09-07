package io.github.paulneja.archdroid.core.terminal

import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import io.github.paulneja.archdroid.core.chroot.ChrootPaths

object TerminalSessionFactory {

    fun create(client: TerminalSessionClient): TerminalSession =
        TerminalSession(
            "su",
            "/",
            arrayOf("su", "-c", ChrootPaths.ENTER_SCRIPT),
            arrayOf(
                "PATH=/sbin:/system/bin:/system/xbin",
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "HOME=/root",
            ),
            null,
            client,
        )
}

package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote.quote

/** Checks the rootfs before starting a terminal session. */
object ChrootHealth {

    fun requiredPaths(chrootDir: String, loginAsRoot: Boolean): List<String> {
        val c = chrootDir
        val paths = mutableListOf(
            "$c/etc/arch-release",
            "$c/bin/bash",
            "$c/etc/passwd",
        )
        if (!loginAsRoot) paths += "$c/bin/su"
        return paths
    }

    fun probeScript(paths: List<String>): String =
        paths.joinToString("\n") { "test -e ${quote(it)} || echo ${quote(it)}" }

    fun missingFrom(output: List<String>): List<String> =
        output.map { it.trim() }.filter { it.isNotEmpty() }

    fun isUsable(missing: List<String>): Boolean = missing.isEmpty()
}

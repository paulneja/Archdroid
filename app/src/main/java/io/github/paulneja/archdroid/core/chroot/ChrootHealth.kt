package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote.quote

/**
 * Cheap precondition check before handing a terminal a chroot to enter.
 *
 * Without it a gutted rootfs fails inside `exec chroot`, the session dies in the same
 * breath it was born, and the screen closes itself -- leaving the user bounced back with
 * nothing said. Naming the missing paths up front turns that into an answer.
 */
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

    /** Shell that echoes back exactly the paths that do not exist. */
    fun probeScript(paths: List<String>): String =
        paths.joinToString("\n") { "test -e ${quote(it)} || echo ${quote(it)}" }

    fun missingFrom(output: List<String>): List<String> =
        output.map { it.trim() }.filter { it.isNotEmpty() }

    fun isUsable(missing: List<String>): Boolean = missing.isEmpty()
}

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

    /** Prints `user:<name>` when the login account is missing, so su would exit at once. */
    fun userProbe(chrootDir: String, user: String): String =
        "grep -q ${quote("^$user:")} ${quote("$chrootDir/etc/passwd")} || echo ${quote("user:$user")}"

    fun existingLoginUser(passwd: List<String>): String? {
        val humans = passwd.mapNotNull { line ->
            val f = line.split(':')
            val uid = f.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            val shell = f.getOrNull(6).orEmpty()
            f[0].takeIf { uid in 1000 until 65000 && !shell.endsWith("nologin") && !shell.endsWith("false") }
        }
        return humans.firstOrNull { it != IMAGE_USER } ?: humans.firstOrNull()
    }

    private const val IMAGE_USER = "alarm"

    fun missingFrom(output: List<String>): List<String> =
        output.map { it.trim() }.filter { it.isNotEmpty() }

    fun isUsable(missing: List<String>): Boolean = missing.isEmpty()
}

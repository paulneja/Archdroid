package io.github.paulneja.archdroid.core.init

import io.github.paulneja.archdroid.core.util.ShellQuote.quote

/** Locks root/root and alarm/alarm from the image; sshd is enabled there. */
object DefaultAccounts {

    val DEFAULTS = listOf("root" to "root", "alarm" to "alarm")

    const val LOCKED_PREFIX = "locked "

    /** perl crypt() handles the image's yescrypt hashes. */
    fun lockScript(chrootDir: String): String {
        val c = quote(chrootDir)
        val d = '$'
        return buildString {
            appendLine("[ -x $c/usr/bin/perl ] || exit 0")
            DEFAULTS.forEach { (user, password) ->
                appendLine("h=$d(grep '^$user:' $c/etc/shadow | cut -d: -f2)")
                appendLine(
                    "if [ -n \"$d{h}\" ] && chroot $c /usr/bin/perl -e " +
                        "'exit(crypt(${'$'}ARGV[0], ${'$'}ARGV[1]) eq ${'$'}ARGV[1] ? 0 : 1)' " +
                        "${quote(password)} \"$d{h}\"; then"
                )
                appendLine("  chroot $c /usr/bin/passwd -l $user >/dev/null && echo '$LOCKED_PREFIX$user'")
                appendLine("fi")
            }
            appendLine(":")
        }
    }

    fun lockedFrom(output: List<String>): List<String> =
        output.map { it.trim() }.filter { it.startsWith(LOCKED_PREFIX) }.map { it.removePrefix(LOCKED_PREFIX) }
}

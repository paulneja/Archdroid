package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.RootTar
import io.github.paulneja.archdroid.core.util.ShellQuote.quote

class InvalidBackupException(message: String) : Exception(message)

object BackupCheck {

    const val TAR_FAILED = "__ARCHDROID_TAR_FAILED__"
    private val MARKER = Regex("""(\./)?etc/arch-release""")

    fun script(archive: String): String = buildString {
        append(RootTar.RESOLVE)

        appendLine("{ \$TAR -tzf ${quote(archive)} 2>/dev/null || echo $TAR_FAILED; } | " +
            "grep -xE '(\\./)?etc/arch-release|$TAR_FAILED'")
        appendLine("true")
    }

    fun requireRootfs(output: List<String>) {
        val lines = output.map { it.trim() }
        if (TAR_FAILED in lines) throw InvalidBackupException("the backup archive is damaged or not a tar.gz")
        if (lines.none { MARKER.matches(it) }) {
            throw InvalidBackupException("the archive does not contain an Arch Linux root filesystem")
        }
    }
}

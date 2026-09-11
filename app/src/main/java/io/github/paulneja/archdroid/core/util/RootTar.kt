package io.github.paulneja.archdroid.core.util

/**
 * Android's toybox `tar` drops permissions and ownership, which would ruin an extracted
 * rootfs, so archive work goes through busybox. Every root manager ships one; we look in
 * their known locations and then on PATH, and only fall back to plain `tar` if there is
 * genuinely nothing else.
 */
object RootTar {

    /** Shell prelude that leaves a usable tar command in $TAR. */
    val RESOLVE: String = buildString {
        appendLine("TAR=\"tar\"")
        appendLine("for bb in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox \\")
        appendLine("          /data/adb/ap/bin/busybox /data/adb/modules/busybox-ndk/system/bin/busybox; do")
        appendLine("  [ -x \"\$bb\" ] && TAR=\"\$bb tar\" && break")
        appendLine("done")
        appendLine("if [ \"\$TAR\" = \"tar\" ]; then")
        appendLine("  bb=\$(command -v busybox 2>/dev/null)")
        appendLine("  [ -n \"\$bb\" ] && TAR=\"\$bb tar\"")
        appendLine("fi")
    }
}

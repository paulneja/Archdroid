package io.github.paulneja.archdroid.core.util

object RootTar {
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

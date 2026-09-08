package io.github.paulneja.archdroid.core.util

/**
 * Android's toybox `tar` does not keep permissions or handle every archive the rootfs
 * needs, so every archive operation runs through the busybox shipped by the root manager,
 * falling back to whatever `tar` is on PATH.
 */
object RootTar {

    /** Shell prelude that leaves a usable tar command in $TAR. */
    val RESOLVE: String = buildString {
        appendLine("TAR=\"tar\"")
        appendLine("for bb in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox; do")
        appendLine("  [ -x \"\$bb\" ] && TAR=\"\$bb tar\" && break")
        appendLine("done")
    }
}

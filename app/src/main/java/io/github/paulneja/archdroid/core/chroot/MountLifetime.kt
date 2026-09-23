package io.github.paulneja.archdroid.core.chroot

/** Keeps user-pinned mounts alive after the last terminal exits. */
object MountLifetime {

    fun shouldUnmount(pinnedByUser: Boolean, remainingSessions: Int): Boolean =
        !pinnedByUser && remainingSessions == 0
}

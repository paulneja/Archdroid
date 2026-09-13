package io.github.paulneja.archdroid.core.chroot

/**
 * Decides when mounts brought up for a terminal session come back down.
 *
 * A chroot left mounted with nothing using it is a loaded gun: any later `rm -rf` on the
 * chroot directory follows the binds out into whatever they point at. The mounts are
 * released as soon as the last session ends, unless the user mounted deliberately -- from
 * Home or via autostart -- in which case taking them down would be the surprise.
 */
object MountLifetime {

    fun shouldUnmount(pinnedByUser: Boolean, remainingSessions: Int): Boolean =
        !pinnedByUser && remainingSessions == 0
}

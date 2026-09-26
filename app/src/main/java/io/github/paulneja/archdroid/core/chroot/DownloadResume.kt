package io.github.paulneja.archdroid.core.chroot

object DownloadResume {
    private const val RANGE_NOT_SATISFIABLE = 416

    /** A 416 for a non-empty partial file means it already holds the whole archive. */
    fun partialIsComplete(responseCode: Int, alreadyBytes: Long): Boolean =
        responseCode == RANGE_NOT_SATISFIABLE && alreadyBytes > 0
}

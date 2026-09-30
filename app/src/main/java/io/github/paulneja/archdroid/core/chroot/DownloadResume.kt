package io.github.paulneja.archdroid.core.chroot

object DownloadResume {
    private const val RANGE_NOT_SATISFIABLE = 416

    fun partialIsComplete(responseCode: Int, alreadyBytes: Long): Boolean =
        responseCode == RANGE_NOT_SATISFIABLE && alreadyBytes > 0
}

package io.github.paulneja.archdroid.core.chroot

class RootfsTooLargeException(message: String) : Exception(message)

object RootfsSizeLimit {
    const val MAX_ARCHIVE_BYTES = 1536L * 1024 * 1024

    fun totalBytes(resumedBytes: Long, responseBytes: Long): Long {
        requireWithinLimit(resumedBytes)
        if (responseBytes < 0) return -1L
        if (responseBytes > MAX_ARCHIVE_BYTES - resumedBytes) {
            throw RootfsTooLargeException("rootfs exceeds the 1.5 GiB safety limit")
        }
        return resumedBytes + responseBytes
    }

    fun requireWithinLimit(bytes: Long) {
        if (bytes > MAX_ARCHIVE_BYTES) {
            throw RootfsTooLargeException("rootfs exceeds the 1.5 GiB safety limit")
        }
    }
}

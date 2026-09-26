package io.github.paulneja.archdroid.core.chroot

object RootfsSecurity {
    private val fingerprintPattern = Regex("[0-9A-F]{40}")
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun requireStableSignature(before: ByteArray, after: ByteArray): ByteArray {
        if (before.isEmpty() || after.isEmpty() || !RootfsVerifier.signaturesMatch(before, after)) {
            throw RootfsVerificationException("rootfs signature changed during download")
        }
        return after
    }

    fun encode(verified: VerifiedRootfs): String =
        "${verified.signerFingerprint}\n${verified.sha256}\n${verified.bytes}\n"

    fun decode(encoded: String): VerifiedRootfs {
        val lines = encoded.lines()
        val fingerprint = lines.getOrNull(0).orEmpty()
        val sha256 = lines.getOrNull(1).orEmpty()
        val bytes = lines.getOrNull(2)?.toLongOrNull()
        if (!fingerprintPattern.matches(fingerprint) ||
            !sha256Pattern.matches(sha256) ||
            bytes == null || bytes <= 0
        ) {
            throw RootfsVerificationException("invalid rootfs verification record")
        }
        return VerifiedRootfs(fingerprint, sha256, bytes)
    }

    fun requireUnchanged(expected: VerifiedRootfs, actual: VerifiedRootfs) {
        if (expected != actual) {
            throw RootfsVerificationException("rootfs changed after verification")
        }
    }
}

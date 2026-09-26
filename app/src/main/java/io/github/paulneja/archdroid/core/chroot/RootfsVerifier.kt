package io.github.paulneja.archdroid.core.chroot

import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

data class VerifiedRootfs(
    val signerFingerprint: String,
    val sha256: String,
    val bytes: Long,
)

class RootfsVerificationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class RootfsVerifier(
    trustedKey: ByteArray,
    expectedFingerprint: String,
) {
    private val expectedFingerprint = expectedFingerprint.uppercase()
    private val publicKey = loadPublicKey(trustedKey, this.expectedFingerprint)

    fun verify(
        archive: File,
        detachedSignature: ByteArray,
        checkActive: () -> Unit = {},
    ): VerifiedRootfs {
        try {
            val decoder = PGPUtil.getDecoderStream(ByteArrayInputStream(detachedSignature))
            val objects = PGPObjectFactory(decoder, BcKeyFingerprintCalculator())
            val signatures = objects.nextObject() as? PGPSignatureList
                ?: throw RootfsVerificationException("invalid detached signature")
            if (signatures.size() != 1 || objects.nextObject() != null) {
                throw RootfsVerificationException("unexpected detached signature contents")
            }

            val signature = signatures[0]
            if (signature.keyID != publicKey.keyID) {
                throw RootfsVerificationException("signature was made by an unexpected key")
            }
            if (signature.signatureType != PGPSignature.BINARY_DOCUMENT) {
                throw RootfsVerificationException("rootfs signature is not a binary document signature")
            }
            if (signature.hashAlgorithm !in ALLOWED_HASH_ALGORITHMS) {
                throw RootfsVerificationException("rootfs signature uses a weak hash algorithm")
            }
            signature.init(BcPGPContentVerifierBuilderProvider(), publicKey)

            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            archive.inputStream().buffered().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    checkActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    signature.update(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytes += read
                }
            }
            if (!signature.verify()) {
                throw RootfsVerificationException("rootfs signature does not match the archive")
            }

            return VerifiedRootfs(
                signerFingerprint = fingerprint(publicKey),
                sha256 = digest.digest().toHex().lowercase(),
                bytes = bytes,
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: RootfsVerificationException) {
            throw failure
        } catch (failure: Throwable) {
            throw RootfsVerificationException("cannot verify rootfs signature", failure)
        }
    }

    private fun loadPublicKey(encoded: ByteArray, expected: String): PGPPublicKey {
        try {
            val rings = PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(ByteArrayInputStream(encoded)),
                BcKeyFingerprintCalculator(),
            )
            val keys = rings.keyRings.asSequence().flatMap { it.publicKeys.asSequence() }
            return keys.firstOrNull { fingerprint(it) == expected }
                ?: throw RootfsVerificationException("trusted key fingerprint does not match the pinned fingerprint")
        } catch (failure: RootfsVerificationException) {
            throw failure
        } catch (failure: Throwable) {
            throw RootfsVerificationException("cannot read trusted rootfs key", failure)
        }
    }

    private fun fingerprint(key: PGPPublicKey): String = key.fingerprint.toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    companion object {
        private val ALLOWED_HASH_ALGORITHMS = setOf(
            HashAlgorithmTags.SHA256,
            HashAlgorithmTags.SHA384,
            HashAlgorithmTags.SHA512,
        )

        fun signaturesMatch(first: ByteArray, second: ByteArray): Boolean =
            MessageDigest.isEqual(first, second)
    }
}

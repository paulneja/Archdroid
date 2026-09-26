package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RootfsSecurityTest {
    private val verified = VerifiedRootfs(
        signerFingerprint = "68B3537F39A313B3E574D06777193F152BDBE6A6",
        sha256 = "3cb36869edfe413672a6e932cc55d7f8386e1a9d3b38663cfb3bc6fe0d146e21",
        bytes = 5160,
    )

    @Test
    fun `keeps a signature only when it is stable around the download`() {
        val signature = byteArrayOf(1, 2, 3)
        assertArrayEquals(signature, RootfsSecurity.requireStableSignature(signature, signature.copyOf()))

        assertThrows(RootfsVerificationException::class.java) {
            RootfsSecurity.requireStableSignature(signature, byteArrayOf(1, 2, 4))
        }
        assertThrows(RootfsVerificationException::class.java) {
            RootfsSecurity.requireStableSignature(byteArrayOf(), byteArrayOf())
        }
    }

    @Test
    fun `persists a strict verification record`() {
        val encoded = RootfsSecurity.encode(verified)
        assertEquals(verified, RootfsSecurity.decode(encoded))

        assertThrows(RootfsVerificationException::class.java) {
            RootfsSecurity.decode("${verified.signerFingerprint}\nnot-a-hash\n${verified.bytes}\n")
        }
    }

    @Test
    fun `rejects any archive change before extraction`() {
        RootfsSecurity.requireUnchanged(verified, verified.copy())

        assertThrows(RootfsVerificationException::class.java) {
            RootfsSecurity.requireUnchanged(verified, verified.copy(bytes = verified.bytes + 1))
        }
        assertThrows(RootfsVerificationException::class.java) {
            RootfsSecurity.requireUnchanged(verified, verified.copy(sha256 = "0".repeat(64)))
        }
        assertThrows(RootfsVerificationException::class.java) {
            RootfsSecurity.requireUnchanged(verified, verified.copy(signerFingerprint = "0".repeat(40)))
        }
    }
}

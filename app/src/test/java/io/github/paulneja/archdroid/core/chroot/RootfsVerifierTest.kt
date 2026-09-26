package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CancellationException

class RootfsVerifierTest {
    @Test
    fun `accepts an archive signed by the pinned key`() {
        withArchive("trusted rootfs fixture\n") { archive ->
            val verified = verifier().verify(archive, SIGNATURE.toByteArray())

            assertEquals(FINGERPRINT, verified.signerFingerprint)
            assertEquals("50725d294ca004f7d5167878bd044541f79b81d828bb2743137e4dacae0ac2d3", verified.sha256)
            assertEquals(23L, verified.bytes)
        }
    }

    @Test
    fun `rejects an archive changed after signing`() {
        withArchive("tampered rootfs fixture\n") { archive ->
            assertThrows(RootfsVerificationException::class.java) {
                verifier().verify(archive, SIGNATURE.toByteArray())
            }
        }
    }

    @Test
    fun `rejects a trusted key whose fingerprint is not pinned`() {
        assertThrows(RootfsVerificationException::class.java) {
            RootfsVerifier(PUBLIC_KEY.toByteArray(), "0000000000000000000000000000000000000000")
        }
    }

    @Test
    fun `rejects signatures made with sha1`() {
        withArchive("trusted rootfs fixture\n") { archive ->
            assertThrows(RootfsVerificationException::class.java) {
                verifier().verify(archive, SHA1_SIGNATURE.toByteArray())
            }
        }
    }

    @Test
    fun `requires the signatures around the download to be identical`() {
        val signature = SIGNATURE.toByteArray()
        assertTrue(RootfsVerifier.signaturesMatch(signature, signature.copyOf()))
        assertFalse(RootfsVerifier.signaturesMatch(signature, signature + 0))
    }

    @Test
    fun `does not turn cancellation into a verification failure`() {
        withArchive("trusted rootfs fixture\n") { archive ->
            val cancellation = CancellationException("paused")
            val thrown = assertThrows(CancellationException::class.java) {
                verifier().verify(archive, SIGNATURE.toByteArray()) { throw cancellation }
            }
            assertSame(cancellation, thrown)
        }
    }

    private fun verifier() = RootfsVerifier(PUBLIC_KEY.toByteArray(), FINGERPRINT)

    private fun withArchive(contents: String, block: (java.io.File) -> Unit) {
        val path = Files.createTempFile("rootfs-verifier", ".tar.gz")
        try {
            Files.writeString(path, contents)
            block(path.toFile())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private companion object {
        const val FINGERPRINT = "330FD9533C3E5F4BE6677DABDDBFB70F868CC472"

        val PUBLIC_KEY = """
            -----BEGIN PGP PUBLIC KEY BLOCK-----

            mQENBGqv+RoBCACkg2QKbGzvc7qwdPPyD0/ibsrDwaaN8oJjJ9iJljqPHlndDYGQ
            Nm9xg6knx8osl9xFLqNwWjYX+dB8GwEors1UFUHgtvvcz0eT3O9K+unjyppywioF
            RTVhBfN+aoiCTB1nSdk6KC/n1k4Okv41i+jMi7qW2Af3QLvauL2d1adpQMgw8YxW
            Ex2WpDX3qiPnTAojrN/7iOFb3O5LEvL/gfxY3efMUev0d/5Yv9ACz2Z/rBSrKSvU
            IunKLvYhpxKS6r302VEXcqIDvtw1eJlwI+KESzcOtkZZtHNBjLyTbd6bTLInmd7t
            TP4zpeBLiJ1VleM6430vIdQ0lwCGbnHJoQXFABEBAAG0L0FyY2hEcm9pZCBUZXN0
            IFNpZ25pbmcgPHRlc3RAYXJjaGRyb2lkLmludmFsaWQ+iQFOBBMBCgA4FiEEMw/Z
            Uzw+X0vmZ32r3b+3D4aMxHIFAmqv+RoCGwMFCwkIBwIGFQoJCAsCBBYCAwECHgEC
            F4AACgkQ3b+3D4aMxHL5Hgf/RNsMxH4lnP6UGVusrQkOZH6CL97uhkXHq8PBKNDN
            /e7efS2CBYgHgKMn7nBCn9ZFkxf6Or4WHrm44Yoq/9+VXqeW6IuJqyydIE1dNs6T
            DP5mYL1Czgvy67klcjF/nqG/w+hfJfXwevvIPWmgPJowKYDdjlxURKH+s2HnF9IL
            rQl6mDGQqjMDg8Wcsj9m8yIbf0EDoiTFEXVmcokJc1+uNooaaRdDAvGcEUmjtSop
            JgFDkwJoFye0Sg1+3BVt1anW3/iI7+pEpCYJpxLUBM1DNWZFUeSdRnyLg7vBGgUk
            cwS/QMDc47lSsQwPIHVl30zUxRB0qfZprJjbI+UYV+CN/g==
            =66jM
            -----END PGP PUBLIC KEY BLOCK-----
        """.trimIndent()

        val SIGNATURE = """
            -----BEGIN PGP SIGNATURE-----

            iQEzBAABCgAdFiEEMw/ZUzw+X0vmZ32r3b+3D4aMxHIFAmqv+RoACgkQ3b+3D4aM
            xHJPAQgAoWUsCUpQGpoBxMkAWZDTZrHVIzigUmDaKPNMNw24wZvraQOECWfw/pOQ
            iaBNqWa0EoKSBC94bfGEB2AYc6icEXISnQEGJjAJ2GzzJoXnsf98MtaIHavvx3wY
            2AMs4EHmpFyfucHZE4ROY16u5/6tLaoncH9BLZvq0068Xo70xoofIKFfB49sFd/d
            dhsg4cZx4llwSPsDQicGY9n2FlbQoZW7V7aaCMmt9ZbXnkpBKGqmjp9PAgtUNKE3
            j1nxW9EvdYYfXVEWMwjVPXqKaaFWiopD51fRUu70t+ii4tYSVE/IdgvGU8Tv0IHR
            zo5LmkOxxKvdKPBfGweUSh23CxPO3w==
            =V37k
            -----END PGP SIGNATURE-----
        """.trimIndent()

        val SHA1_SIGNATURE = """
            -----BEGIN PGP SIGNATURE-----

            iQEzBAABAgAdFiEEMw/ZUzw+X0vmZ32r3b+3D4aMxHIFAmqv/LwACgkQ3b+3D4aM
            xHLc8Af/aGj4Y5eTR1xmjRBDA3cp6YKfJUNXQ7G81Q9VHGeTlK7sqzlymIYl62Bx
            ovPIYfjbp0dcBikBnt78yqrjXBIrnidmcj7jXgzQpVjQeOhqG5oXvuLYTwPCliTN
            Pv230jCWOGzxOG80HisOZRQNRsGOxAQUOpsWNh6xFaCprjlXZKrq4pOfuqb3XzI9
            9FmbqIH8old5o8QMQqmgHAm1cfGgWpWKP0Bqm9PQwm1HN0uAmKGYqfudLjcQoij2
            9+CZpuFapy1tor7FfvP3lvg0vUsSFO/mczLau3xUV9XKBBdsxf2HYtUZ66OPKDJr
            SvZnFn3YNlaR++AWvk2kwwnCa5qAdg==
            =VwGs
            -----END PGP SIGNATURE-----
        """.trimIndent()
    }
}

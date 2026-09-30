package io.github.paulneja.archdroid.ui.install

import io.github.paulneja.archdroid.data.model.RootfsDownloadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallPlanTest {
    @Test
    fun `loads the saved mode until the user changes it`() {
        assertEquals(
            RootfsDownloadMode.SECURE,
            InstallPlan.loadedDownloadMode(
                current = RootfsDownloadMode.FAST,
                saved = RootfsDownloadMode.SECURE,
                userChanged = false,
            ),
        )
        assertEquals(
            RootfsDownloadMode.FAST,
            InstallPlan.loadedDownloadMode(
                current = RootfsDownloadMode.FAST,
                saved = RootfsDownloadMode.SECURE,
                userChanged = true,
            ),
        )
    }

    @Test
    fun `accepts a normal linux username`() {
        assertTrue(InstallPlan.usernameValid("paul"))
        assertTrue(InstallPlan.usernameValid("_svc"))
        assertTrue(InstallPlan.usernameValid("arch-1"))
    }

    @Test
    fun `rejects usernames the shell or useradd would choke on`() {
        assertFalse(InstallPlan.usernameValid(""))
        assertFalse(InstallPlan.usernameValid("1arch"))
        assertFalse(InstallPlan.usernameValid("Arch"))
        assertFalse(InstallPlan.usernameValid("a b"))
        assertFalse(InstallPlan.usernameValid("root; rm -rf /"))
        assertFalse(InstallPlan.usernameValid("a".repeat(33)))
    }

    @Test
    fun `sudo is always installed even when logging in as root`() {
        assertTrue(InstallPlan.packagesFor(emptySet()).contains("sudo"))
        assertTrue(InstallPlan.packagesFor(setOf("git")).contains("sudo"))
    }

    @Test
    fun `compat init interpreter is installed without optional packages`() {
        assertTrue(InstallPlan.packagesFor(emptySet()).contains("python"))
    }

    @Test
    fun `package list keeps the selection and never duplicates sudo`() {
        val pkgs = InstallPlan.packagesFor(setOf("git", "sudo", "vim"))
        assertEquals(1, pkgs.count { it == "sudo" })
        assertTrue(pkgs.containsAll(listOf("git", "vim")))
    }

    @Test
    fun `install is blocked without enough free space`() {
        assertFalse(
            InstallPlan.canInstall(
                freeBytes = 1_000_000_000L,
                username = "arch",
                password = "secret",
                loginAsRoot = false,
            )
        )
    }

    @Test
    fun `install is blocked by a short password`() {
        assertFalse(
            InstallPlan.canInstall(
                freeBytes = 9_000_000_000L,
                username = "arch",
                password = "ab",
                loginAsRoot = false,
            )
        )
    }

    @Test
    fun `install needs no password when logging in as root`() {
        assertTrue(
            InstallPlan.canInstall(
                freeBytes = 9_000_000_000L,
                username = "",
                password = "",
                loginAsRoot = true,
            )
        )
    }

    @Test
    fun `log line keeps step and detail together`() {
        assertEquals("download: https://mirror/rootfs.tar.gz",
            InstallPlan.logLine("DOWNLOAD", "https://mirror/rootfs.tar.gz"))
        assertEquals("verify", InstallPlan.logLine("VERIFY", ""))
    }
}

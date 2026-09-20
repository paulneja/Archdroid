package io.github.paulneja.archdroid.ui.install

import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.service.InstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallUiMapperTest {

    @Test
    fun `running coordinator state keeps wizard on progress page after recreation`() {
        val mapped = InstallUiMapper.apply(
            InstallWizardViewModel.UiState(page = 0, username = "paul"),
            InstallState.Running(ChrootInstaller.Step.DOWNLOAD, "mirror", 50, 100, 10, 5),
            listOf("download: mirror"),
        )

        assertEquals(4, mapped.page)
        assertTrue(mapped.running)
        assertEquals("paul", mapped.username)
        assertEquals(50L, mapped.progress?.downloadedBytes)
    }

    @Test
    fun `failed coordinator state exposes error and retryable progress page`() {
        val mapped = InstallUiMapper.apply(
            InstallWizardViewModel.UiState(page = 4),
            InstallState.Failed(ChrootInstaller.Step.EXTRACT, "tar failed"),
            emptyList(),
        )

        assertFalse(mapped.running)
        assertEquals(4, mapped.page)
        assertEquals("tar failed", mapped.error)
    }

    @Test
    fun `paused state preserves byte progress and form fields`() {
        val mapped = InstallUiMapper.apply(
            InstallWizardViewModel.UiState(username = "paul", password = "secret"),
            InstallState.Paused(
                step = ChrootInstaller.Step.DOWNLOAD,
                downloadedBytes = 40,
                totalBytes = 100,
            ),
            listOf("paused"),
        )

        assertEquals(4, mapped.page)
        assertFalse(mapped.running)
        assertEquals(40L, mapped.progress?.downloadedBytes)
        assertEquals("paul", mapped.username)
        assertEquals("secret", mapped.password)
    }

    @Test
    fun `success opens done page`() {
        val mapped = InstallUiMapper.apply(
            InstallWizardViewModel.UiState(page = 4),
            InstallState.Succeeded,
            emptyList(),
        )

        assertEquals(5, mapped.page)
        assertTrue(mapped.done)
        assertFalse(mapped.running)
    }
}

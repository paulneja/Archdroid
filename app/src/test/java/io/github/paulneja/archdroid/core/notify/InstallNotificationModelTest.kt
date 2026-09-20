package io.github.paulneja.archdroid.core.notify

import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.service.InstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallNotificationModelTest {

    @Test
    fun `running download shows bounded progress and pause cancel actions`() {
        val model = InstallNotificationModel.from(
            InstallState.Running(ChrootInstaller.Step.DOWNLOAD, "", 25, 100, 10, 8)
        )

        assertEquals(25, model.progressPercent)
        assertEquals(
            listOf(InstallNotificationAction.PAUSE, InstallNotificationAction.CANCEL),
            model.actions,
        )
        assertTrue(model.ongoing)
        assertTrue(model.foreground)
    }

    @Test
    fun `download progress is clamped to notification range`() {
        val model = InstallNotificationModel.from(
            InstallState.Running(
                ChrootInstaller.Step.DOWNLOAD,
                downloadedBytes = 150,
                totalBytes = 100,
            )
        )

        assertEquals(100, model.progressPercent)
    }

    @Test
    fun `unknown total uses indeterminate progress`() {
        val model = InstallNotificationModel.from(
            InstallState.Running(
                ChrootInstaller.Step.DOWNLOAD,
                downloadedBytes = 25,
                totalBytes = -1,
            )
        )

        assertNull(model.progressPercent)
    }

    @Test
    fun `paused install offers resume and cancel`() {
        val model = InstallNotificationModel.from(
            InstallState.Paused(
                step = ChrootInstaller.Step.DOWNLOAD,
                downloadedBytes = 25,
                totalBytes = 100,
            )
        )

        assertEquals(
            listOf(InstallNotificationAction.RESUME, InstallNotificationAction.CANCEL),
            model.actions,
        )
        assertTrue(model.ongoing)
        assertFalse(model.foreground)
    }

    @Test
    fun `failure offers retry and is not ongoing`() {
        val model = InstallNotificationModel.from(
            InstallState.Failed(ChrootInstaller.Step.VERIFY, "md5 mismatch")
        )

        assertEquals(listOf(InstallNotificationAction.RETRY), model.actions)
        assertFalse(model.ongoing)
        assertEquals("md5 mismatch", model.message)
    }

    @Test
    fun `success opens terminal and is not ongoing`() {
        val model = InstallNotificationModel.from(InstallState.Succeeded)

        assertEquals(listOf(InstallNotificationAction.OPEN_TERMINAL), model.actions)
        assertFalse(model.ongoing)
    }

    @Test
    fun `stopping work retains foreground protection without actions`() {
        val model = InstallNotificationModel.from(
            InstallState.Stopping(
                target = InstallState.StopTarget.CANCEL,
                running = InstallState.Running(ChrootInstaller.Step.PACKAGES),
            )
        )

        assertTrue(model.foreground)
        assertTrue(model.actions.isEmpty())
    }

    @Test
    fun `failure summary is single line and bounded`() {
        val secret = "password\n" + "x".repeat(300)
        val model = InstallNotificationModel.from(
            InstallState.Failed(ChrootInstaller.Step.USER, secret)
        )

        assertFalse(model.message.contains('\n'))
        assertTrue(model.message.length <= 160)
    }
}

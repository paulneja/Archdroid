package io.github.paulneja.archdroid.ui.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionStatusTest {
    @Test
    fun `denial matters only on notification permission Android versions`() {
        assertFalse(NotificationPermissionStatus.denied(apiLevel = 32, permissionGranted = false))
        assertTrue(NotificationPermissionStatus.denied(apiLevel = 33, permissionGranted = false))
        assertFalse(NotificationPermissionStatus.denied(apiLevel = 36, permissionGranted = true))
    }
}

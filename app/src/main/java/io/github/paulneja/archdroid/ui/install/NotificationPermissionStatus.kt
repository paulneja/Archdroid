package io.github.paulneja.archdroid.ui.install

object NotificationPermissionStatus {
    fun denied(apiLevel: Int, permissionGranted: Boolean): Boolean =
        apiLevel >= 33 && !permissionGranted
}

package io.github.paulneja.archdroid.ui.home

import io.github.paulneja.archdroid.core.chroot.ChrootStatus

internal enum class OrbitalState { DORMANT, READY, ACTIVE, WORKING, ERROR }

internal fun orbitalState(status: ChrootStatus, busy: Boolean, failed: Boolean, active: Boolean): OrbitalState = when {
    busy || status == ChrootStatus.UNKNOWN -> OrbitalState.WORKING
    failed -> OrbitalState.ERROR
    status != ChrootStatus.MOUNTED -> OrbitalState.DORMANT
    active -> OrbitalState.ACTIVE
    else -> OrbitalState.READY
}

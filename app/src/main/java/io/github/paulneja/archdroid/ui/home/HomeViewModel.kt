package io.github.paulneja.archdroid.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.chroot.ChrootPaths
import io.github.paulneja.archdroid.core.chroot.ChrootStatus
import io.github.paulneja.archdroid.core.chroot.MountManager
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val chroot: ChrootController = ServiceLocator.chroot(app)
    val status = chroot.status

    private val _kernel = MutableStateFlow("—")
    val kernel = _kernel.asStateFlow()

    private val _diskUsage = MutableStateFlow("—")
    val diskUsage = _diskUsage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    /** Set when a mount or unmount did not do what was asked, so the UI can say so. */
    /** Live mount points under the chroot, straight from /proc/mounts. */
    private val _activeMounts = MutableStateFlow<List<String>>(emptyList())
    val activeMounts = _activeMounts.asStateFlow()

    private val _mountError = MutableStateFlow<String?>(null)
    val mountError = _mountError.asStateFlow()

    fun clearMountError() {
        _mountError.value = null
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            chroot.refresh()
            _activeMounts.value = MountManager.activeMounts()
            _kernel.value = RootManager.outputOf("uname -r").firstOrNull() ?: "—"
            _diskUsage.value = RootManager
                .outputOf("du -sh ${ChrootPaths.CHROOT_DIR} 2>/dev/null | cut -f1")
                .firstOrNull() ?: "—"
        }
    }

    fun toggleMount() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _mountError.value = null
            try {
                val result = when (status.value) {
                    ChrootStatus.MOUNTED -> chroot.stop()
                    ChrootStatus.INSTALLED -> chroot.prepareSession(pin = true)
                    else -> null
                }
                if (result != null && !result.ok) {
                    _mountError.value = result.log.filter { it.isNotBlank() }.takeLast(4)
                        .joinToString("\n")
                        .ifBlank { "operation failed" }
                }
                chroot.refresh()
                refresh()
            } finally {
                _busy.value = false
            }
        }
    }
}

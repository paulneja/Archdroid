package io.github.paulneja.archdroid.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.chroot.ChrootPaths
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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            chroot.refresh()
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
            try {
                when (status.value) {
                    io.github.paulneja.archdroid.core.chroot.ChrootStatus.MOUNTED -> chroot.stop()
                    io.github.paulneja.archdroid.core.chroot.ChrootStatus.INSTALLED -> chroot.prepareSession()
                    else -> {}
                }
                chroot.refresh()
            } finally {
                _busy.value = false
            }
        }
    }
}

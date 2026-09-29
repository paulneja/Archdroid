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
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import io.github.paulneja.archdroid.core.terminal.SessionActivity

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val chroot: ChrootController = ServiceLocator.chroot(app)
    val status = chroot.status
    private val sessions = ServiceLocator.sessionStore(app)
    private val desktop = ServiceLocator.desktop(app)
    val desktopState = desktop.state
    val preparing = sessions.preparing
    val tabs = sessions.tabs
    val installState = ServiceLocator.installCoordinator(app).state
    private val _terminalWorking = MutableStateFlow(false)
    val terminalWorking = _terminalWorking.asStateFlow()

    /** Home alone samples activity. No background timer or frame-by-frame root queries. */
    suspend fun watchActivity() {
        var sample = 0
        while (currentCoroutineContext().isActive) {
            try {
                val leaders = sessions.tabs.value.mapNotNull { tab ->
                    if (tab.processIdentity.isCompleted && !tab.closing.value) {
                        tab.processIdentity.await().getOrNull()
                    } else null
                }
                _terminalWorking.value = leaders.isNotEmpty() && SessionActivity.hasWork(
                    leaders, RootManager.outputOfScript(SessionActivity.readScript(leaders)),
                )
                if (sample++ % 6 == 0 && !_busy.value && !sessions.preparing.value) {
                    chroot.refresh()
                    desktop.refresh()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _terminalWorking.value = false
            }
            delay(1_000)
        }
    }

    fun newTerminal() = sessions.createSession()
    fun resumeDesktop() = desktop.show()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _kernel = MutableStateFlow("—")
    val kernel = _kernel.asStateFlow()

    private val _diskUsage = MutableStateFlow("—")
    val diskUsage = _diskUsage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

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
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                chroot.refresh()
                _activeMounts.value = MountManager.activeMounts()
                _kernel.value = RootManager.outputOf("uname -r").firstOrNull() ?: "—"
                _diskUsage.value = RootManager
                    .outputOf("du -xsh ${ChrootPaths.CHROOT_DIR} 2>/dev/null | cut -f1")
                    .firstOrNull() ?: "—"
            } finally {
                _refreshing.value = false
            }
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
                    ChrootStatus.INSTALLED -> chroot.prepareSession()
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

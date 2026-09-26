package io.github.paulneja.archdroid.ui.install

import android.app.Application
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.core.service.ArchDroidService
import io.github.paulneja.archdroid.core.service.InstallState
import io.github.paulneja.archdroid.data.model.EnvConfig
import io.github.paulneja.archdroid.data.model.InstallConfig
import io.github.paulneja.archdroid.data.model.RootfsDownloadMode
import io.github.paulneja.archdroid.di.ServiceLocator
import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstallWizardViewModel(app: Application) : AndroidViewModel(app) {

    private val app = app
    private val coordinator = ServiceLocator.installCoordinator(app)
    private val settings = ServiceLocator.settings(app)
    private var downloadModeChanged = false

    companion object {
        val AVAILABLE_PACKAGES = InstallPlan.AVAILABLE_PACKAGES
        const val MIN_FREE_BYTES = InstallPlan.MIN_FREE_BYTES
    }

    data class UiState(
        val page: Int = 0,
        val downloadMode: RootfsDownloadMode = RootfsDownloadMode.FAST,
        val selectedPackages: Set<String> = setOf("git", "nano", "zsh", "fastfetch"),
        val username: String = "arch",
        val password: String = "",
        val loginAsRoot: Boolean = false,
        val freeBytes: Long = -1L,
        val kernel: String = "",
        val running: Boolean = false,
        val progress: ChrootInstaller.Progress? = null,
        val log: List<String> = emptyList(),
        val error: String? = null,
        val done: Boolean = false,
        val installState: InstallState = InstallState.Idle,
        val notificationsDenied: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(coordinator.state, coordinator.log) { state, log -> state to log }
                .collect { (state, log) ->
                    _ui.update { InstallUiMapper.apply(it, state, log) }
                }
        }
        viewModelScope.launch {
            val stat = StatFs("/data")
            val kernel = RootManager.outputOf("uname -r").firstOrNull().orEmpty()
            _ui.update { it.copy(freeBytes = stat.availableBytes, kernel = kernel) }
        }
        viewModelScope.launch {
            val savedMode = settings.current().downloadMode
            _ui.update { current ->
                current.copy(
                    downloadMode = InstallPlan.loadedDownloadMode(
                        current = current.downloadMode,
                        saved = savedMode,
                        userChanged = downloadModeChanged,
                    ),
                )
            }
        }
    }

    fun setPage(page: Int) = _ui.update { it.copy(page = page) }
    fun nextPage() = _ui.update { it.copy(page = it.page + 1) }
    fun prevPage() = _ui.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }

    fun setDownloadMode(mode: RootfsDownloadMode) {
        downloadModeChanged = true
        _ui.update { it.copy(downloadMode = mode) }
    }

    fun togglePackage(pkg: String) = _ui.update {
        it.copy(
            selectedPackages = if (pkg in it.selectedPackages) it.selectedPackages - pkg
            else it.selectedPackages + pkg
        )
    }

    fun setUsername(name: String) = _ui.update { it.copy(username = name) }
    fun setPassword(password: String) = _ui.update { it.copy(password = password) }
    fun setLoginAsRoot(login: Boolean) = _ui.update { it.copy(loginAsRoot = login) }
    fun setNotificationsDenied(denied: Boolean) = _ui.update {
        it.copy(notificationsDenied = denied)
    }

    fun usernameValid(): Boolean = InstallPlan.usernameValid(_ui.value.username)

    fun canInstall(): Boolean = _ui.value.let {
        InstallPlan.canInstall(it.freeBytes, it.username, it.password, it.loginAsRoot)
    }

    fun install() {
        if (_ui.value.installState is InstallState.Running ||
            _ui.value.installState is InstallState.Queued ||
            _ui.value.installState is InstallState.Paused
        ) return
        val s = _ui.value
        _ui.update { it.copy(page = 4, running = true, error = null, log = emptyList()) }
        coordinator.queue(
            InstallConfig(
                downloadMode = s.downloadMode,
                packages = InstallPlan.packagesFor(s.selectedPackages),
                username = if (s.loginAsRoot) "" else s.username.trim(),
                password = s.password,
                env = EnvConfig(),
            )
        )
        ArchDroidService.start(app)
    }

    fun pause() = ArchDroidService.pause(app)
    fun resume() = ArchDroidService.resume(app)
    fun cancel() = ArchDroidService.cancel(app)
    fun retry() = ArchDroidService.retry(app)
    fun acknowledgeTerminalState() = coordinator.acknowledgeTerminalState()
}

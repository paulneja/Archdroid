package io.github.paulneja.archdroid.ui.install

import android.app.Application
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.chroot.ChrootPaths
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.data.model.EnvConfig
import io.github.paulneja.archdroid.data.model.InstallConfig
import io.github.paulneja.archdroid.data.model.UserConfig
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstallWizardViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = ServiceLocator.settings(app)
    private val chroot = ServiceLocator.chroot(app)
    private val installer = ChrootInstaller(app)

    companion object {
        val AVAILABLE_PACKAGES = InstallPlan.AVAILABLE_PACKAGES
        const val MIN_FREE_BYTES = InstallPlan.MIN_FREE_BYTES
    }

    data class UiState(
        val page: Int = 0,
        val mirror: String = ChrootPaths.DEFAULT_MIRROR,
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
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val stat = StatFs("/data")
            val kernel = RootManager.outputOf("uname -r").firstOrNull().orEmpty()
            _ui.update { it.copy(freeBytes = stat.availableBytes, kernel = kernel) }
        }
    }

    fun setPage(page: Int) = _ui.update { it.copy(page = page) }
    fun nextPage() = _ui.update { it.copy(page = it.page + 1) }
    fun prevPage() = _ui.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }

    fun setMirror(mirror: String) = _ui.update { it.copy(mirror = mirror) }

    fun togglePackage(pkg: String) = _ui.update {
        it.copy(
            selectedPackages = if (pkg in it.selectedPackages) it.selectedPackages - pkg
            else it.selectedPackages + pkg
        )
    }

    fun setUsername(name: String) = _ui.update { it.copy(username = name) }
    fun setPassword(password: String) = _ui.update { it.copy(password = password) }
    fun setLoginAsRoot(login: Boolean) = _ui.update { it.copy(loginAsRoot = login) }

    fun usernameValid(): Boolean = InstallPlan.usernameValid(_ui.value.username)

    fun canInstall(): Boolean = _ui.value.let {
        InstallPlan.canInstall(it.freeBytes, it.username, it.password, it.loginAsRoot)
    }

    fun install() {
        if (_ui.value.running) return
        val s = _ui.value
        _ui.update { it.copy(page = 4, running = true, error = null, log = emptyList()) }

        viewModelScope.launch {
            val config = InstallConfig(
                mirror = s.mirror.trim().ifBlank { ChrootPaths.DEFAULT_MIRROR },
                packages = InstallPlan.packagesFor(s.selectedPackages, s.loginAsRoot),
                username = if (s.loginAsRoot) "" else s.username.trim(),
                password = s.password,
                env = EnvConfig(),
            )
            val result = installer.install(config) { progress ->
                _ui.update { state ->
                    state.copy(
                        progress = progress,
                        log = (state.log + InstallPlan.logLine(progress.step.name, progress.detail))
                            .takeLast(200),
                    )
                }
            }
            result.fold(
                onSuccess = {
                    settings.updateMirror(config.mirror)
                    settings.updateUser(
                        UserConfig(
                            username = if (s.loginAsRoot) "root" else config.username,
                            loginAsRoot = s.loginAsRoot,
                        )
                    )
                    chroot.refresh()
                    _ui.update { it.copy(running = false, done = true, page = 5) }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            running = false,
                            error = e.message ?: e.toString(),
                            log = it.log + "ERROR: ${e.message}",
                        )
                    }
                },
            )
        }
    }
}

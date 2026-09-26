package io.github.paulneja.archdroid.ui.desktop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.desktop.DesktopController
import io.github.paulneja.archdroid.core.desktop.DesktopEnvironment
import io.github.paulneja.archdroid.core.desktop.DisplayEngine
import io.github.paulneja.archdroid.core.desktop.LorieDefaults
import io.github.paulneja.archdroid.core.desktop.SessionType
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DesktopViewModel(app: Application) : AndroidViewModel(app) {

    private val controller: DesktopController = ServiceLocator.desktop(app)
    val state: StateFlow<DesktopController.State> = controller.state

    data class Choices(
        val toInstall: Set<DesktopEnvironment> = setOf(DesktopEnvironment.XFCE),
        val installWayland: Boolean = true,
        val desktop: DesktopEnvironment? = null,
        val session: SessionType = SessionType.X11,
        val engine: DisplayEngine = DisplayEngine.X11_NATIVE,
    )

    private val _extraKeys = MutableStateFlow(LorieDefaults.extraKeysShown(app))
    val extraKeys: StateFlow<Boolean> = _extraKeys.asStateFlow()

    fun setExtraKeys(shown: Boolean) {
        LorieDefaults.setExtraKeysShown(getApplication(), shown)
        _extraKeys.value = shown
    }

    private val _choices = MutableStateFlow(Choices())
    val choices: StateFlow<Choices> = _choices.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            controller.refresh()
            val installed = state.value.installed
            _choices.update { c ->
                c.copy(desktop = c.desktop?.takeIf { it in installed } ?: installed.minByOrNull { it.ordinal })
            }
        }
    }

    fun toggleInstall(de: DesktopEnvironment) = _choices.update {
        it.copy(toInstall = if (de in it.toInstall) it.toInstall - de else it.toInstall + de)
    }

    fun setInstallWayland(value: Boolean) = _choices.update { it.copy(installWayland = value) }
    fun selectDesktop(de: DesktopEnvironment) = _choices.update {
        it.copy(desktop = de, session = if (de.supportsWayland) it.session else SessionType.X11)
    }
    fun selectSession(type: SessionType) = _choices.update { it.copy(session = type) }
    fun selectEngine(engine: DisplayEngine) = _choices.update { it.copy(engine = engine) }

    fun install() = controller.install(_choices.value.toInstall, _choices.value.installWayland)

    fun start(width: Int, height: Int) {
        val c = _choices.value
        val de = c.desktop ?: return
        LorieDefaults.apply(getApplication())
        viewModelScope.launch { controller.start(de, c.session, c.engine, width, height) }
    }

    fun open() = controller.show()

    fun stop() {
        viewModelScope.launch { controller.stop() }
    }

    fun clearError() = controller.clearError()
}

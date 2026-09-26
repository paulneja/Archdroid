package io.github.paulneja.archdroid.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.protect.AppProtection
import io.github.paulneja.archdroid.data.model.AppSettings
import io.github.paulneja.archdroid.data.model.ProtectionConfig
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProtectionViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.settings(app)
    private val protection: AppProtection = ServiceLocator.protection(app)

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val status: StateFlow<AppProtection.Status> = protection.status

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun refresh() {
        viewModelScope.launch { protection.refresh() }
    }

    fun grantAll() = runBusy { protection.grantBackgroundAllowances() }

    fun setExtended(enabled: Boolean) = runBusy { protection.setExtended(enabled) }

    fun updateProtection(config: ProtectionConfig) {
        viewModelScope.launch {
            repo.updateProtection(config)
            // Give the watchdog a moment to write the new score before showing it.
            kotlinx.coroutines.delay(300)
            protection.refresh()
        }
    }

    fun setTerminalSurvival(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateBehavior(settings.value.behavior.copy(terminalSurvival = enabled))
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun runBusy(block: suspend () -> Result<Unit>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = block().exceptionOrNull()?.let { it.message ?: it.toString() }
            _busy.value = false
        }
    }
}

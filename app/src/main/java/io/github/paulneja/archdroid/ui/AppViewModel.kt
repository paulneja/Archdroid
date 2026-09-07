package io.github.paulneja.archdroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.data.SettingsRepository
import io.github.paulneja.archdroid.data.model.AppSettings
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val settingsRepository: SettingsRepository = ServiceLocator.settings(app)
    val chroot: ChrootController = ServiceLocator.chroot(app)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    enum class RootState { CHECKING, GRANTED, DENIED }

    private val _rootState = MutableStateFlow(RootState.CHECKING)
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    init {
        checkRoot()
    }

    fun checkRoot() {
        viewModelScope.launch {
            _rootState.value = RootState.CHECKING
            val granted = RootManager.checkRoot()
            if (granted) chroot.refresh()
            _rootState.value = if (granted) RootState.GRANTED else RootState.DENIED
        }
    }
}

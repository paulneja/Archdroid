package io.github.paulneja.archdroid.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.chroot.ChrootStatus
import io.github.paulneja.archdroid.core.chroot.MountManager
import io.github.paulneja.archdroid.core.chroot.MountPlan
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.ui.install.InstallPlan
import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.SettingsRepository
import io.github.paulneja.archdroid.data.model.AppSettings
import io.github.paulneja.archdroid.data.model.BehaviorConfig
import io.github.paulneja.archdroid.data.model.EnvConfig
import io.github.paulneja.archdroid.data.model.MountConfig
import io.github.paulneja.archdroid.data.model.UserConfig
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: SettingsRepository = ServiceLocator.settings(app)
    val chroot: ChrootController = ServiceLocator.chroot(app)
    private val installCoordinator = ServiceLocator.installCoordinator(app)

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    sealed interface BackupState {
        data object Idle : BackupState
        data class Running(val label: String) : BackupState
        data class Success(val message: String) : BackupState
        data class Error(val message: String) : BackupState

        data class MountsBusy(val points: List<String>) : BackupState
    }

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState = _backupState.asStateFlow()

    private val _activeMounts = MutableStateFlow<List<String>>(emptyList())
    val activeMounts = _activeMounts.asStateFlow()

    /** Result of the last change that touched the live chroot; null when it went through. */
    private val _applyError = MutableStateFlow<String?>(null)
    val applyError = _applyError.asStateFlow()

    fun clearApplyError() {
        _applyError.value = null
    }

    init {
        refreshMounts()
    }

    fun refreshMounts() {
        viewModelScope.launch {
            _activeMounts.value = MountManager.activeMounts()
        }
    }

    fun updateMounts(m: MountConfig) {
        viewModelScope.launch {
            val invalid = MountPlan.validate(m)
            if (invalid.isNotEmpty()) {
                _applyError.value = invalid.joinToString("\n")
                return@launch
            }
            repo.updateMounts(m)
            if (chroot.status.value == ChrootStatus.MOUNTED) {
                val result = MountManager.reconcile(m)
                _applyError.value = result.log.takeUnless { result.ok }?.joinToString("\n")
            } else {
                _applyError.value = null
            }
            _activeMounts.value = MountManager.activeMounts()
        }
    }

    fun updateEnv(e: EnvConfig) {
        viewModelScope.launch {
            repo.updateEnv(e)
            if (chroot.status.value != ChrootStatus.NOT_INSTALLED) {
                chroot.applyEnv(e)
            }
        }
    }

    fun updateUser(u: UserConfig, newPassword: String = "") {
        viewModelScope.launch {
            if (!u.loginAsRoot && !InstallPlan.usernameValid(u.username)) {
                _applyError.value = getApplication<Application>()
                    .getString(R.string.user_invalid_name, u.username)
                return@launch
            }
            repo.updateUser(u)
            _applyError.value = if (u.loginAsRoot || chroot.status.value == ChrootStatus.NOT_INSTALLED) {
                null
            } else {
                chroot.ensureUser(u.username, newPassword.takeIf { it.isNotBlank() }).exceptionOrNull()
                    ?.let { it.message ?: it.toString() }
            }
        }
    }

    fun updateBehavior(b: BehaviorConfig) {
        viewModelScope.launch { repo.updateBehavior(b) }
    }

    fun updateDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repo.updateDynamicColor(enabled) }
    }

    fun exportBackup(target: Uri) {
        if (_backupState.value is BackupState.Running) return
        viewModelScope.launch {
            _backupState.value = BackupState.Running("export")
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val tmp = File(getApplication<Application>().filesDir, "arch-backup-$stamp.tar.gz")
            try {
                chroot.exportBackup(tmp).getOrThrow()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(target)?.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    } ?: error("cannot open destination")
                }
                _backupState.value = BackupState.Success(tmp.name)
            } catch (e: Exception) {
                _backupState.value = errorFor(e)
            } finally {
                deleteAsRoot(tmp)
                refreshAfterBackup()
            }
        }
    }

    fun importBackup(source: Uri) {
        if (_backupState.value is BackupState.Running) return
        viewModelScope.launch {
            _backupState.value = BackupState.Running("import")
            val tmp = File(getApplication<Application>().filesDir, "restore.tar.gz")
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(source)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("cannot open source")
                }
                chroot.importBackup(tmp).getOrThrow()
                _backupState.value = BackupState.Success("restore.tar.gz")
            } catch (e: Exception) {
                _backupState.value = errorFor(e)
            } finally {
                deleteAsRoot(tmp)
                refreshAfterBackup()
            }
        }
    }

    /** The export is written by root; the app may not own it. */
    private suspend fun deleteAsRoot(file: File) {
        if (!withContext(Dispatchers.IO) { file.delete() } && file.exists()) {
            RootManager.exec("rm -f ${quote(file.absolutePath)}")
        }
    }

    private suspend fun refreshAfterBackup() {
        chroot.refresh()
        _activeMounts.value = MountManager.activeMounts()
    }

    fun resetChroot() {
        if (_backupState.value is BackupState.Running) return
        viewModelScope.launch {
            _backupState.value = BackupState.Running("reset")
            chroot.reset().fold(
                onSuccess = {
                    installCoordinator.acknowledgeTerminalState()
                    _backupState.value = BackupState.Success("reset")
                    _activeMounts.value = emptyList()
                },
                onFailure = { _backupState.value = errorFor(it) },
            )
        }
    }

    private fun errorFor(t: Throwable): BackupState = when (t) {
        is ChrootController.MountsBusyException -> BackupState.MountsBusy(t.points)
        else -> BackupState.Error(t.message ?: t.toString())
    }

    fun clearBackupState() {
        _backupState.value = BackupState.Idle
    }
}

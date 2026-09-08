package io.github.paulneja.archdroid.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.chroot.ChrootStatus
import io.github.paulneja.archdroid.core.chroot.MountManager
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

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    sealed interface BackupState {
        data object Idle : BackupState
        data class Running(val label: String) : BackupState
        data class Success(val message: String) : BackupState
        data class Error(val message: String) : BackupState

        /** Refused: something is still mounted under the chroot, deleting would escape it. */
        data class MountsBusy(val points: List<String>) : BackupState
    }

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState = _backupState.asStateFlow()

    /** What is actually mounted under the chroot right now, straight from /proc/mounts. */
    private val _activeMounts = MutableStateFlow<List<String>>(emptyList())
    val activeMounts = _activeMounts.asStateFlow()

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
            repo.updateMounts(m)
            if (chroot.status.value == ChrootStatus.MOUNTED) {
                MountManager.ensureMounted(m)
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
            repo.updateUser(u)
            if (newPassword.isNotBlank() && !u.loginAsRoot) {
                chroot.runInsideChroot("echo ${quote("${u.username}:$newPassword")} | chpasswd")
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
        viewModelScope.launch {
            _backupState.value = BackupState.Running("export")
            try {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                val tmp = File(getApplication<Application>().filesDir, "arch-backup-$stamp.tar.gz")
                chroot.exportBackup(tmp).getOrThrow()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(target)?.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    } ?: error("cannot open destination")
                }
                tmp.delete()
                _backupState.value = BackupState.Success(tmp.name)
            } catch (e: Exception) {
                _backupState.value = errorFor(e)
            }
        }
    }

    fun importBackup(source: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.Running("import")
            try {
                val tmp = File(getApplication<Application>().filesDir, "restore.tar.gz")
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(source)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("cannot open source")
                }
                chroot.importBackup(tmp).getOrThrow()
                tmp.delete()
                _backupState.value = BackupState.Success("restore.tar.gz")
                _activeMounts.value = MountManager.activeMounts()
            } catch (e: Exception) {
                _backupState.value = errorFor(e)
            }
        }
    }

    fun resetChroot() {
        viewModelScope.launch {
            _backupState.value = BackupState.Running("reset")
            chroot.reset().fold(
                onSuccess = {
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

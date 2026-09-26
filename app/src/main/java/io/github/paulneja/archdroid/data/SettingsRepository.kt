package io.github.paulneja.archdroid.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.paulneja.archdroid.data.model.AppSettings
import io.github.paulneja.archdroid.data.model.BehaviorConfig
import io.github.paulneja.archdroid.data.model.DevMode
import io.github.paulneja.archdroid.data.model.EnvConfig
import io.github.paulneja.archdroid.data.model.InitMode
import io.github.paulneja.archdroid.data.model.MountConfig
import io.github.paulneja.archdroid.data.model.ProtectionConfig
import io.github.paulneja.archdroid.data.model.RootfsDownloadMode
import io.github.paulneja.archdroid.data.model.UserConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "archdroid_settings")

object DownloadModeSettings {
    fun decode(savedMode: String?, legacyMirror: String?): RootfsDownloadMode =
        savedMode?.let { saved ->
            RootfsDownloadMode.entries.firstOrNull { it.name == saved }
        } ?: if (legacyMirror?.startsWith("https://") == true) {
            RootfsDownloadMode.SECURE
        } else {
            RootfsDownloadMode.FAST
        }
}

@SuppressLint("SdCardPath")
class SettingsRepository(private val context: Context) {

    private object Keys {
        val MOUNT_PROC = booleanPreferencesKey("mount_proc")
        val MOUNT_SYS = booleanPreferencesKey("mount_sys")
        val MOUNT_DEV = booleanPreferencesKey("mount_dev")
        val DEV_MODE = stringPreferencesKey("dev_mode")
        val MOUNT_DEVPTS = booleanPreferencesKey("mount_devpts")
        val MOUNT_SHM = booleanPreferencesKey("mount_shm")
        val SHM_SIZE = intPreferencesKey("shm_size_mb")
        val BIND_SHARED = booleanPreferencesKey("bind_shared")
        val SHARED_SRC = stringPreferencesKey("shared_src")
        val SHARED_DST = stringPreferencesKey("shared_dst")
        val SHARED_RO = booleanPreferencesKey("shared_ro")

        val EXTRA_ENV = stringSetPreferencesKey("extra_env")
        val HOSTNAME = stringPreferencesKey("hostname")
        val DNS1 = stringPreferencesKey("dns1")
        val DNS2 = stringPreferencesKey("dns2")
        val LANG = stringPreferencesKey("lang")

        val USERNAME = stringPreferencesKey("username")
        val LOGIN_AS_ROOT = booleanPreferencesKey("login_as_root")

        val AUTOSTART = booleanPreferencesKey("autostart_boot")
        val TERMINAL_SURVIVAL = booleanPreferencesKey("terminal_survival")
        val STARTUP_SCRIPT = stringPreferencesKey("startup_script")
        val SHUTDOWN_SCRIPT = stringPreferencesKey("shutdown_script")

        val OOM_ENABLED = booleanPreferencesKey("oom_enabled")
        val OOM_SCORE = intPreferencesKey("oom_score")

        val INIT_MODE = stringPreferencesKey("init_mode")

        val DOWNLOAD_MODE = stringPreferencesKey("download_mode")
        val LEGACY_MIRROR = stringPreferencesKey("mirror")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            mounts = MountConfig(
                proc = p[Keys.MOUNT_PROC] ?: true,
                sys = p[Keys.MOUNT_SYS] ?: true,
                dev = p[Keys.MOUNT_DEV] ?: true,
                devMode = p[Keys.DEV_MODE]?.let { name ->
                    DevMode.entries.firstOrNull { it.name == name }
                } ?: DevMode.BIND,
                devPts = p[Keys.MOUNT_DEVPTS] ?: true,
                shm = p[Keys.MOUNT_SHM] ?: true,
                shmSizeMb = p[Keys.SHM_SIZE] ?: 256,
                bindSharedStorage = p[Keys.BIND_SHARED] ?: false,
                sharedStorageSource = p[Keys.SHARED_SRC] ?: "/sdcard",
                sharedStorageTarget = p[Keys.SHARED_DST] ?: "/mnt/sdcard",
                sharedStorageReadOnly = p[Keys.SHARED_RO] ?: false,
            ),
            env = EnvConfig(
                extraEnv = (p[Keys.EXTRA_ENV] ?: emptySet()).mapNotNull {
                    val i = it.indexOf('=')
                    if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
                }.toMap(),
                hostname = p[Keys.HOSTNAME] ?: "archdroid",
                dnsPrimary = p[Keys.DNS1] ?: "8.8.8.8",
                dnsSecondary = p[Keys.DNS2] ?: "1.1.1.1",
                lang = p[Keys.LANG] ?: "en_US.UTF-8",
            ),
            user = UserConfig(
                username = p[Keys.USERNAME] ?: "arch",
                loginAsRoot = p[Keys.LOGIN_AS_ROOT] ?: false,
            ),
            behavior = BehaviorConfig(
                autostartOnBoot = p[Keys.AUTOSTART] ?: false,
                terminalSurvival = p[Keys.TERMINAL_SURVIVAL] ?: true,
                startupScript = p[Keys.STARTUP_SCRIPT] ?: "",
                shutdownScript = p[Keys.SHUTDOWN_SCRIPT] ?: "",
            ),
            protection = ProtectionConfig(
                oomEnabled = p[Keys.OOM_ENABLED] ?: false,
                oomScore = (p[Keys.OOM_SCORE] ?: -900).coerceIn(-1000, -900),
            ),
            initMode = p[Keys.INIT_MODE]?.let { name ->
                InitMode.entries.firstOrNull { it.name == name }
            } ?: InitMode.COMPAT,
            downloadMode = DownloadModeSettings.decode(
                p[Keys.DOWNLOAD_MODE],
                p[Keys.LEGACY_MIRROR],
            ),
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun updateMounts(m: MountConfig) {
        context.dataStore.edit { p ->
            p[Keys.MOUNT_PROC] = m.proc
            p[Keys.MOUNT_SYS] = m.sys
            p[Keys.MOUNT_DEV] = m.dev
            p[Keys.DEV_MODE] = m.devMode.name
            p[Keys.MOUNT_DEVPTS] = m.devPts
            p[Keys.MOUNT_SHM] = m.shm
            p[Keys.SHM_SIZE] = m.shmSizeMb
            p[Keys.BIND_SHARED] = m.bindSharedStorage
            p[Keys.SHARED_SRC] = m.sharedStorageSource
            p[Keys.SHARED_DST] = m.sharedStorageTarget
            p[Keys.SHARED_RO] = m.sharedStorageReadOnly
        }
    }

    suspend fun updateEnv(e: EnvConfig) {
        context.dataStore.edit { p ->
            p[Keys.EXTRA_ENV] = e.extraEnv.map { (k, v) -> "$k=$v" }.toSet()
            p[Keys.HOSTNAME] = e.hostname
            p[Keys.DNS1] = e.dnsPrimary
            p[Keys.DNS2] = e.dnsSecondary
            p[Keys.LANG] = e.lang
        }
    }

    /** False until a user name was stored, by the installer or in Settings. */
    suspend fun hasChosenUser(): Boolean = context.dataStore.data.first().contains(Keys.USERNAME)

    suspend fun updateUser(u: UserConfig) {
        context.dataStore.edit { p ->
            p[Keys.USERNAME] = u.username
            p[Keys.LOGIN_AS_ROOT] = u.loginAsRoot
        }
    }

    suspend fun updateBehavior(b: BehaviorConfig) {
        context.dataStore.edit { p ->
            p[Keys.AUTOSTART] = b.autostartOnBoot
            p[Keys.TERMINAL_SURVIVAL] = b.terminalSurvival
            p[Keys.STARTUP_SCRIPT] = b.startupScript
            p[Keys.SHUTDOWN_SCRIPT] = b.shutdownScript
        }
    }

    suspend fun updateProtection(c: ProtectionConfig) {
        context.dataStore.edit { p ->
            p[Keys.OOM_ENABLED] = c.oomEnabled
            p[Keys.OOM_SCORE] = c.oomScore
        }
    }

    suspend fun updateInitMode(mode: InitMode) {
        context.dataStore.edit { it[Keys.INIT_MODE] = mode.name }
    }

    suspend fun updateDownloadMode(mode: RootfsDownloadMode) {
        context.dataStore.edit { it[Keys.DOWNLOAD_MODE] = mode.name }
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }
}

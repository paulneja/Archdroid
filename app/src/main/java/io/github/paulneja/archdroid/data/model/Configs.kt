package io.github.paulneja.archdroid.data.model

import android.annotation.SuppressLint

/**
 * BIND (the default) shares Android's /dev, so device nodes and file descriptor links work as
 * on the host. PRIVATE uses an isolated tmpfs, which is safer if the chroot directory is removed
 * by hand while mounted.
 */
enum class DevMode { PRIVATE, BIND }

@SuppressLint("SdCardPath")
data class MountConfig(
    /** A tmpfs on /run, as systemd expects; services put sockets and PID files there. */
    val run: Boolean = true,
    val proc: Boolean = true,
    val sys: Boolean = true,
    val dev: Boolean = true,
    val devMode: DevMode = DevMode.BIND,
    val devPts: Boolean = true,
    val shm: Boolean = true,
    val shmSizeMb: Int = 256,
    val bindSharedStorage: Boolean = false,
    val sharedStorageSource: String = "/sdcard",
    val sharedStorageTarget: String = "/mnt/sdcard",
    val sharedStorageReadOnly: Boolean = false,
)

data class EnvConfig(
    val extraEnv: Map<String, String> = emptyMap(),
    val hostname: String = "archdroid",
    val dnsPrimary: String = "8.8.8.8",
    val dnsSecondary: String = "1.1.1.1",
    val lang: String = "en_US.UTF-8",
)

data class UserConfig(
    val username: String = "arch",
    val loginAsRoot: Boolean = false,
)

data class BehaviorConfig(
    val autostartOnBoot: Boolean = false,
    /** Keep a foreground notification while terminals run, so Android does not reclaim them. */
    val terminalSurvival: Boolean = true,
    val startupScript: String = "",
    val shutdownScript: String = "",
)

/** How services start: a systemd-compatible layer, or real systemd when the kernel allows it. */
enum class InitMode { COMPAT, SYSTEMD }

/** Opt-in: -1000 means the low memory killer never picks ArchDroid, even under pressure. */
data class ProtectionConfig(
    val oomEnabled: Boolean = false,
    val oomScore: Int = -900,
)

enum class RootfsDownloadMode { FAST, SECURE }

data class InstallConfig(
    val downloadMode: RootfsDownloadMode,
    val packages: List<String>,
    val username: String,
    val password: String,
    val env: EnvConfig,
)

data class AppSettings(
    val mounts: MountConfig = MountConfig(),
    val env: EnvConfig = EnvConfig(),
    val user: UserConfig = UserConfig(),
    val behavior: BehaviorConfig = BehaviorConfig(),
    val protection: ProtectionConfig = ProtectionConfig(),
    val initMode: InitMode = InitMode.COMPAT,
    val downloadMode: RootfsDownloadMode = RootfsDownloadMode.FAST,
    val dynamicColor: Boolean = true,
)

package io.github.paulneja.archdroid.data.model

data class MountConfig(
    val proc: Boolean = true,
    val sys: Boolean = true,
    val dev: Boolean = true,
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
    val startupScript: String = "",
    val shutdownScript: String = "",
)

data class InstallConfig(
    val mirror: String,
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
    val mirror: String = io.github.paulneja.archdroid.core.chroot.ChrootPaths.DEFAULT_MIRROR,
    val dynamicColor: Boolean = true,
)

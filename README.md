# ArchDroid

ArchDroid runs an Arch Linux ARM root filesystem inside a chroot on a rooted Android
device. It includes a guided installer, a tabbed terminal, mount controls, backups, and
foreground notifications for long-running installation work.

The project is currently built for `arm64-v8a` devices.

## Features

- Guided Arch Linux ARM installation
- Resumable root filesystem downloads
- Foreground progress notifications with pause, resume, cancel, and retry actions
- Tabbed terminal with extra keys for common terminal controls
- Configurable `/proc`, `/sys`, `/dev`, shared storage, and tmpfs mounts
- Root or regular-user login with `sudo` support
- DNS, hostname, shell, startup script, and shutdown script settings
- Root filesystem backup and restore
- Optional mount setup after boot
- Safe single-tab close that leaves other terminal sessions running

## Requirements

- Android 8.0 or newer
- A rooted `arm64-v8a` device
- A root manager that can grant access to ArchDroid
- At least 2.5 GB of free space, plus room for additional packages and user files
- An internet connection for the initial installation

ArchDroid needs root because Android does not allow regular apps to create mounts or enter
a chroot. Review every root request before granting it.

## Build

Clone the repository and build the debug APK:

```bash
git clone https://github.com/paulneja/Archdroid.git
cd Archdroid
./gradlew assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The build requires JDK 17 and an Android SDK capable of compiling API 37. Native terminal
support is built for ARM64 with Android NDK 28.

## First run

1. Open ArchDroid and grant root access.
2. Choose a mirror and configure the Linux user.
3. Select any additional packages.
4. Start the installation and keep the progress notification available.
5. Open the terminal when installation finishes.

Downloads can be paused or resumed without discarding the partial archive. A failed
installation can retry from its last safe checkpoint.

## Mount safety

Bind mounts can expose Android files inside the chroot. Unmount the environment before
manually changing or deleting its root filesystem. Shared storage should only be enabled
when Linux applications need it; read-only mode is safer when write access is unnecessary.

Closing a terminal tab stops only that tab's process session. Closing the final tab returns
to the main screen without changing the current mount state.

## Documentation

- [Background work and notifications](docs/specs/background-work-design.md)
- [Install notifications and terminal close](docs/specs/install-notifications-and-terminal-close.md)
- [Implementation record](docs/plans/install-notifications-and-terminal-close.md)

## Continuous builds and releases

Every push and pull request runs the unit tests and lint checks, then builds debug and
unsigned release APKs. Both files are available from the workflow run artifacts for 14
days.

Tags starting with `v` also publish a GitHub Release with generated notes and the unsigned
release APK attached:

```bash
git tag v0.1.0
git push origin v0.1.0
```

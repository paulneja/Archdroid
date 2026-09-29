# ArchDroid

ArchDroid runs an Arch Linux ARM root filesystem inside a chroot on a rooted Android
device. It includes a guided installer, persistent terminal tabs, mount controls, backups,
foreground notifications, and an in-app Linux desktop.

The project is currently built for `arm64-v8a` devices.

## Features

- Guided Arch Linux ARM installation
- Resumable root filesystem downloads
- Fast HTTP and direct HTTPS download modes
- Root filesystem verification with the pinned Arch Linux ARM OpenPGP key
- Foreground progress notifications with pause, resume, cancel, and retry actions
- Tabbed terminal with extra keys and adjustable text from 1–32 sp
- Chrome, Aurora and Ember styles, animated glass controls and an interactive status orb
- Optional performance mode with flat controls and no visual effects or animations
- Persistent and protected terminal tabs with survival mode
- Configurable `/proc`, `/sys`, `/dev`, shared storage, and tmpfs mounts
- Root or regular-user login with `sudo` support
- DNS, hostname, shell, startup script, and shutdown script settings
- Root filesystem backup and restore
- Optional mount setup after boot
- Safe single-tab close that leaves other terminal sessions running
- KDE Plasma, XFCE, LXQt, and MATE desktop sessions
- Embedded Termux:X11 and localhost VNC display engines
- X11 and nested Wayland sessions, software rendering, touchpad input, and desktop audio
- systemd compatibility services with optional real systemd when the kernel supports it
- Battery, OOM, phantom-process, and process-cgroup protection controls

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
git clone --recursive https://github.com/paulneja/Archdroid.git
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
2. Choose the fast HTTP or direct HTTPS download mode and configure the Linux user.
3. Select any additional packages.
4. Start the installation and keep the progress notification available.
5. Open the terminal when installation finishes.

Downloads can be paused or resumed without discarding the partial archive. A failed
installation can retry from its last safe checkpoint.

The root filesystem is accepted only when its detached signature matches the bundled
Arch Linux ARM build key. The signature is fetched over HTTPS before and after the archive
download, then checked again immediately before extraction. HTTP mode only affects the
large archive transfer; signature verification is mandatory in both modes.

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

The nested desktop resize integration test uses a local Xvfb display and checks that
only compositor windows follow host size changes and that the helper exits with its session:

```bash
python3 scripts/test-desktop-fit.py
```

It requires Python 3, libX11, Xvfb and xrandr on the build machine.

Every push and pull request runs the unit tests and lint checks, then builds debug and
signed release APKs. Both files are available from the workflow run artifacts for 14 days.

Tags starting with `v` also publish a GitHub Release with generated notes and the signed
release APK attached:

```bash
git tag v0.2.0
git push origin v0.2.0
```

ArchDroid is licensed under the GNU GPLv3. Third-party components keep their original
licenses; see [NOTICE](NOTICE) and the license files shipped with each component.

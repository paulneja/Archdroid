# ArchDroid

Arch Linux ARM on rooted Android, with a terminal and an embedded Linux desktop.
ArchDroid handles installation, mounts, backups and session controls in one app.

## Features

- Guided installation with verified, resumable downloads and progress notifications
- Persistent terminal tabs, extra keys, text size controls and root or user login
- KDE Plasma, XFCE, LXQt and MATE desktops through Termux:X11 or localhost VNC
- X11 and nested Wayland sessions, touchpad input and desktop audio
- Mount controls, shared storage, backups and optional mount setup after boot
- Chrome, Aurora and Ember styles, with a performance mode that disables effects
- Process protection controls and systemd compatibility services

## Requirements

Android 8.0 or newer, a rooted ARM64 device, and at least 2.5 GB of free storage.
Additional desktop packages need more space. Installation requires internet access.

Root access is required to create mounts and enter the chroot. Unmount the environment
before manually changing or deleting its root filesystem.

## Get started

Download the signed APK from [Releases](https://github.com/paulneja/Archdroid/releases),
install it, grant root access, and follow the installer. See the
[usage guide](docs/usage.md) for download modes, mount safety and terminal behavior.

To build from source:

```bash
git clone --recursive https://github.com/paulneja/Archdroid.git
cd Archdroid
./gradlew :app:assembleDebug
```

The build needs JDK 17, Android SDK API 37 and NDK 28.2.13676358.
[Build instructions](docs/build.md) cover signing, tests and releases.

## Documentation

- [User guide](docs/usage.md)
- [Build and release guide](docs/build.md)
- [Repository layout](docs/structure.md)
- [Design and development notes](docs/README.md)
- [Latest changes](docs/releases/0.3.5.md)

## License

GPL-3.0-or-later. Third-party components retain their original licenses; see
[LICENSE](LICENSE), [NOTICE](NOTICE) and [LICENSES](LICENSES/).
Release downloads include complete source archives and bundled license notices.

# Building ArchDroid

## Toolchain

- JDK 17
- Android SDK with API 37 and Build Tools 36.0.0
- Android NDK 28.2.13676358 and CMake 3.22.1
- Git with recursive submodule support

Set `ANDROID_HOME` to your SDK directory, or use an untracked `local.properties`
file containing `sdk.dir=/path/to/Android/Sdk`.

## Checkout and build

```bash
git clone --recursive https://github.com/paulneja/Archdroid.git
cd Archdroid
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For an existing checkout, run `git submodule update --init --recursive` first.
The application and embedded display engine target `arm64-v8a`.

## Signed release

Provide these environment variables through your local secret store:

| Variable | Value |
|---|---|
| `ARCHDROID_KEYSTORE_FILE` | Absolute path to the signing keystore |
| `ARCHDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ARCHDROID_KEY_ALIAS` | Signing key alias |
| `ARCHDROID_KEY_PASSWORD` | Signing key password |

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Use the existing signing key when updating an installed release. Without all four
variables, a release build is unsigned. Keep keystores and credentials outside Git.

## Checks

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
python3 scripts/test-desktop-fit.py
```

The desktop resize check needs Python 3, libX11, Xvfb and xrandr. It checks window
selection, resizing after rotation, and helper shutdown when its session exits.

## CI and releases

The Android workflow runs tests and lint, builds debug and release APKs, and retains
artifacts for 14 days. Signed builds use repository secrets with the variable names
listed above, plus `ARCHDROID_KEYSTORE_BASE64` for the base64-encoded keystore.

Use the `archdroid-local` runner label and set the `ARCHDROID_RUNNER` repository
variable to that label when using a local runner. Workflow dispatch also lets you
select a runner. Fork pull requests do not receive signing secrets.

Tags beginning with `v` publish the signed APK and full source. Release notes live
in `docs/releases/`. The publishing job uses a hosted runner; when hosted capacity
is unavailable, publish the verified local artifacts with the GitHub CLI.

```bash
python3 scripts/package-source.py ArchDroid-v0.3.5-source.tar.gz v0.3.5
```

Use this script for release source archives: it includes recursively checked-out
submodules, their licenses, and the exact source revision in `SOURCE_COMMIT`.

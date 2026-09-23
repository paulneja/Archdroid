# Install notifications and terminal close: implementation record

This file records the shipped work and the checks that matter when the flow changes again.

## Installation

- [x] Move the install job out of `InstallWizardViewModel`.
- [x] Keep one process-wide coordinator and reject duplicate starts.
- [x] Preserve partial downloads across pause, cancel, network failure, and retry.
- [x] Save phase checkpoints so retry does not repeat completed destructive work.
- [x] Keep pause and cancel in a stopping state until the worker returns.
- [x] Redact password-setting errors.

## Notifications

- [x] Register `install` and `events` channels.
- [x] Run active installs as a `dataSync` foreground service.
- [x] Add Pause, Resume, Cancel, Retry, and Open Terminal actions.
- [x] Show progress, transfer rate, and ETA when available.
- [x] Keep installs working when notification permission is denied.
- [x] Refresh the permission warning after returning from system settings.

## Terminal close

- [x] Capture PID, session ID, and kernel start time through root.
- [x] Revalidate identity before signalling any process.
- [x] Kill only members of the selected POSIX session.
- [x] Use shell builtin reads and immediate `SIGKILL`; do not wait on `SIGTERM`.
- [x] Keep sibling tabs alive and preserve the selected tab after removal.
- [x] Return to Home after the final tab closes without changing mount state.

## Verification commands

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleRelease --no-daemon --console=plain
```

Device checks:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell dumpsys package io.github.paulneja.archdroid.debug
adb shell dumpsys activity services io.github.paulneja.archdroid.debug
adb shell dumpsys notification --noredact
```

For terminal isolation, open two tabs, record their `su` and shell PIDs, close one tab, and
confirm only that POSIX session disappears. Closing the last tab should return to Home in
well under one second.

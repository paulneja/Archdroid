# Background work and notifications

Status: partially shipped

## Why this exists

The first installer ran inside `InstallWizardViewModel`. Leaving the screen destroyed its
scope, and Android soon moved the app to the cached process group. On the test phone the
download fell from roughly 62 KB/s to zero while the same mirror delivered about 4.8 MB/s
to a computer on the same network.

The install job therefore belongs to the application process, not to a screen. A foreground
service keeps that process eligible to run while Android treats the notification as the
user-visible explanation for the work.

## Current design

`InstallCoordinator` owns one install job and publishes `InstallState` through a
`StateFlow`. The wizard observes that state and sends commands. It does not own the job.

`ArchDroidService` handles `START`, `PAUSE`, `CANCEL`, and `RETRY` intents. Active work uses
the `dataSync` foreground-service type. Paused work drops out of the foreground and keeps a
normal notification with Resume and Cancel actions.

There are two notification channels:

| Channel | Importance | Use |
|---|---:|---|
| `install` | low | Ongoing progress, pause, resume, and cancel |
| `events` | default | Completion and failure results |

Android 13 and newer require `POST_NOTIFICATIONS`. A denied permission does not block the
install, but the wizard keeps a visible warning and refreshes it when the app resumes.

## Download recovery

Downloads keep a `.part` file and resume with HTTP Range requests. Network failures retry
up to five times with 2, 4, 8, and 16 second delays between attempts. Any byte progress
resets the failure count. HTTP errors, checksum failures, and installer failures are not
treated as transient network problems.

Installation phases are checkpointed. Resume starts at the last unfinished phase instead
of extracting over a root filesystem that has already been configured. Changing mirrors
invalidates the saved archive and checkpoint.

## Process lifetime

The current release keeps installation state for the life of the app process. It does not
restore a running coroutine after the process itself is killed. The partial archive and
phase checkpoint still allow a later retry to continue safely.

Terminal sessions remain screen-owned. Moving them into a process-wide store and adding a
long-lived mounted-session notification are separate changes and are not simulated by a
ViewModel.

## Device checks

- The foreground service remains active while an install is running.
- Progress continues after leaving the wizard and while the screen is off.
- Pause stops byte growth without deleting the partial archive.
- Resume continues from the saved byte count.
- Cancel stops active work and leaves resumable files intact.
- Completion opens Terminal; failure offers Retry.

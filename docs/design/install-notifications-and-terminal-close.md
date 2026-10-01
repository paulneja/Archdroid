# Install notifications and terminal close

Status: shipped

## Install flow

`InstallCoordinator` serializes start, pause, resume, cancel, and retry. Duplicate service
intents cannot create a second install job. Progress includes the current phase, detail,
downloaded bytes, total bytes, transfer rate, and ETA when those values are known.

The ongoing notification offers Pause and Cancel. A paused notification offers Resume and
Cancel. Success offers Open Terminal, and failure offers Retry with a short single-line
error. Password-setting failures use a safe label so credentials cannot reach logs or a
notification.

Pause and cancel first enter a stopping state. The UI and foreground service do not claim
that work stopped until the installer coroutine has actually returned. Completed and
cancelled states can be acknowledged so a later install starts from a clean state.

## Closing a terminal tab

The close button targets only its own `TerminalSession`. Other tabs and their processes are
left alone.

The direct Termux child becomes a root `su` process, so the app cannot terminate it with
`TerminalSession.finishIfRunning()`. ArchDroid captures the process identity through the
root shell as soon as the session starts. That identity contains the PID, POSIX session ID,
and kernel start time. The start time prevents a reused PID from pointing at an unrelated
process.

Closing a tab runs one small root script. It reads `/proc/*/stat` with shell builtins,
selects only members of the captured POSIX session, rechecks each PID and start time, and
sends `SIGKILL` to children before the leader. There are no external `cat` processes,
sleeps, name matches, broad path matches, or global `pkill` calls. A second pass catches a
child created during the first scan.

The tab disappears after the root command completes. Closing the final tab returns to Home
without waiting for an unmount; mount state is left unchanged.

## Failure handling

- A second tap while a close is running is ignored.
- Failure leaves the tab available and shows the root error.
- A process that exits naturally follows the normal Termux callback path.
- Notification permission denial never changes installer success into failure.
- Retry after a failed or cancelled install reuses the saved configuration and checkpoint.

## Verification

Unit tests cover session parsing, PID reuse, command scope, tab selection, coordinator state
transitions, notification models, checkpoint resume, and credential redaction.

On the Ulefone Armor 29 Ultra, closing a root-backed terminal session took about 224 ms from
tap to both session processes disappearing. The final tab returned to Home and left the
chroot mount state untouched.

# Using ArchDroid

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


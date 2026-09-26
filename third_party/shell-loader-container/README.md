Empty Gradle parent for `:shell-loader:stub`.

Termux:X11's `lorie` module depends on `project(':shell-loader:stub')`. Pointing `:shell-loader`
at the real `third_party/termux-x11/shell-loader` would configure Termux:X11's own launcher app,
which ArchDroid does not build, so the parent project lives in this build-file-less directory.

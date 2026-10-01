# Repository layout

| Path | Contents |
|---|---|
| `app/src/main/java/io/github/paulneja/archdroid/core/` | Root access, chroot setup, terminal processes, desktop sessions and services |
| `app/src/main/java/io/github/paulneja/archdroid/data/` | Settings and persisted application state |
| `app/src/main/java/io/github/paulneja/archdroid/di/` | Application dependencies |
| `app/src/main/java/io/github/paulneja/archdroid/ui/` | Compose screens, controls and themes |
| `app/src/main/cpp/` | Native terminal integration |
| `app/src/main/assets/` | Desktop helpers, init compatibility scripts and shaders |
| `app/src/test/` | Unit and regression tests |
| `third_party/termux-x11/` | Pinned Termux:X11 submodule and its native dependencies |
| `third_party/shell-loader-container/` | Gradle container for the upstream shell-loader stub |
| `scripts/` | Full source packaging and desktop integration checks |
| `docs/` | User guides, build instructions, design notes and release notes |
| `LICENSE`, `LICENSES/`, `NOTICE` | Project license and third-party notices |

The Android application is the `:app` Gradle module. `:lorie` embeds the upstream
X11 display engine, and `:shell-loader:stub` supplies its loader. Both point into
the pinned submodule rather than a second copy of the upstream source.

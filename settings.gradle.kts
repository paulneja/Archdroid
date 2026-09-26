pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // JitPack builds arbitrary GitHub repos; only let it serve the two libraries that need it.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.topjohnwu.libsu")
                includeGroup("com.termux.termux-app")
            }
        }
    }
}
rootProject.name = "ArchDroid"
include(":app")

// Termux:X11's X server, embedded as the desktop display (GPLv3, pinned git submodule).
include(":lorie")
project(":lorie").projectDir = file("third_party/termux-x11/lorie")
include(":shell-loader:stub")
project(":shell-loader").projectDir = file("third_party/shell-loader-container")
project(":shell-loader:stub").projectDir = file("third_party/termux-x11/shell-loader/stub")

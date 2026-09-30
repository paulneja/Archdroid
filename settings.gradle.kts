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

include(":lorie")
project(":lorie").projectDir = file("third_party/termux-x11/lorie")
include(":shell-loader:stub")
project(":shell-loader").projectDir = file("third_party/shell-loader-container")
project(":shell-loader:stub").projectDir = file("third_party/termux-x11/shell-loader/stub")

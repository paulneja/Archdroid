plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Termux:X11 bakes the host's applicationId into its BuildConfig, but only the base one: the
// debug build's ".debug" suffix is invisible to it, so it would look up (and send the X server
// connection to) the release package. Give its debug variant the full id.
val archdroidApplicationId: String by extra("io.github.paulneja.archdroid")
val archdroidDebugSuffix: String by extra(".debug")

project(":lorie") {
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            buildTypes.getByName("debug") {
                buildConfigField("String", "APPLICATION_ID", "\"$archdroidApplicationId$archdroidDebugSuffix\"")
            }
        }
    }
}

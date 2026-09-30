plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

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

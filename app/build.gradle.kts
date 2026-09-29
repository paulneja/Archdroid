plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseStoreFile = providers.environmentVariable("ARCHDROID_KEYSTORE_FILE")
val releaseStorePassword = providers.environmentVariable("ARCHDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ARCHDROID_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ARCHDROID_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.orNull.isNullOrBlank() } &&
    releaseStoreFile.orNull?.let(::file)?.isFile == true

val licenseAssets = layout.buildDirectory.dir("generated/licenseAssets")
val generateLicenseAssets by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
    from(rootProject.file("LICENSES"))
    from(rootProject.file("third_party/termux-x11")) {
        include("**/COPYING*", "**/LICENSE*", "**/NOTICE*")
        exclude("**/build/**", "**/.cxx/**", "**/.git/**")
        into("termux-x11")
    }
    into(licenseAssets.map { it.dir("licenses") })
}

android {
    namespace = "io.github.paulneja.archdroid"
    compileSdk = 37

    defaultConfig {
        applicationId = rootProject.extra["archdroidApplicationId"] as String
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.3.4"

        ndk { abiFilters += "arm64-v8a" }
    }

    // The published Termux JNI library is not aligned for 16 KB pages.
    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = rootProject.extra["archdroidDebugSuffix"] as String
        }
    }

    sourceSets.getByName("main").assets.srcDir(licenseAssets.get().asFile)

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // The installed rootfs is aarch64-only.
        disable += "ChromeOsAbiSupport"
    }

    packaging {
        resources.pickFirsts += "META-INF/LICENSE.md"
        resources.pickFirsts += "META-INF/NOTICE.md"
        // The X server is started by app_process straight from the APK, so libraries must stay
        // uncompressed and page aligned inside it.
        jniLibs.useLegacyPackaging = false
        // Prefer the locally built 16 KB-aligned libtermux.so.
        jniLibs.pickFirsts += "**/libtermux.so"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":lorie"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.libsu.core)
    implementation(libs.termux.terminal.view)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncycastle.openpgp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.named("preBuild") { dependsOn(generateLicenseAssets) }

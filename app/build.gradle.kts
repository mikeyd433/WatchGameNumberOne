import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Commit count on main only goes up, so every build installs over the last one
// whether it came from CI or a local build.
val commitCount: Int = runCatching {
    val out = ByteArrayOutputStream()
    project.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        standardOutput = out
    }
    out.toString().trim().toInt()
}.getOrDefault(1)

// Play upload key. Never committed: CI writes it from GitHub secrets, and a local
// build can point at it with the same environment variables.
val uploadKeystore: String? = System.getenv("BEZEL_UPLOAD_KEYSTORE")
val uploadPassword: String? = System.getenv("BEZEL_UPLOAD_PASSWORD")
val hasUploadKey = !uploadKeystore.isNullOrBlank() && !uploadPassword.isNullOrBlank() && file(uploadKeystore!!).exists()

android {
    namespace = "com.mikeyd.bezeltown"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mikeyd.bezeltown"
        // Wear OS 3, the oldest OS the Galaxy Watch 4 shipped with.
        minSdk = 30
        targetSdk = 35
        versionCode = commitCount
        versionName = "0.1.$commitCount"

        // Wear OS watches are ARM; skipping x86 roughly halves the native code shipped.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // A shared, committed debug key: every build (CI or local) is signed the
        // same way, so updates install over each other and keep the save.
        // It's a throwaway debug key, not a release key.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(uploadKeystore!!)
                storeType = "pkcs12"
                storePassword = uploadPassword
                keyAlias = "upload"
                keyPassword = uploadPassword
            }
        }
    }

    buildTypes {
        // The build to put on the watch: R8-optimized, non-debuggable and shipped
        // with the libraries' startup profiles, so Compose and the game run
        // much faster than the debug build.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // For sideloading: signed with the shared debug key so builds install over each other.
            signingConfig = signingConfigs.getByName("debug")
        }
        // For Google Play: the same optimized build, signed with the private upload key.
        // `./gradlew :app:bundlePlay` makes the .aab to upload to the Play Console.
        create("play") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = if (hasUploadKey) signingConfigs.getByName("upload") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Compiled Filament materials must not be compressed.
        noCompress += "filamat"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.filament.android)
}

// An unsigned bundle is useless to Play. Fail before any task runs, so no
// unsigned .aab is ever left behind in build/outputs.
gradle.taskGraph.whenReady {
    if (!hasUploadKey && allTasks.any { it.path == ":app:bundlePlay" }) {
        throw GradleException(
            "bundlePlay needs the Play upload key: set BEZEL_UPLOAD_KEYSTORE (path to the .p12) " +
                "and BEZEL_UPLOAD_PASSWORD.",
        )
    }
}

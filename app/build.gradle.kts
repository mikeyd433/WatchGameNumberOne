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

android {
    namespace = "com.mikeyd.bezeltown"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mikeyd.bezeltown"
        // Wear OS 3, the oldest OS the Galaxy Watch 4 shipped with.
        minSdk = 30
        targetSdk = 34
        versionCode = commitCount
        versionName = "0.1.$commitCount"
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
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideloaded for now; sign with the debug key so release builds install too.
            signingConfig = signingConfigs.getByName("debug")
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

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nothingx.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nothingx.wear"
        minSdk = 30 // Wear OS 3 (Galaxy Watch 4 and newer)
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // Fixed debug signing key committed at the repo root (debug.keystore — not
    // secret, standard "android"/"android" debug password). Without this,
    // Gradle falls back to each machine/CI run's own auto-generated
    // ~/.android/debug.keystore, so every fresh CI build (or a build from a
    // different machine) signs with a different key. Android then refuses
    // `adb install` over an already-installed app with
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE — confirmed hitting this installing
    // a CI-built APK over a locally-built one. Pinning the key here means
    // every build, from any machine or CI run, signs identically.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        // Compose compiler version matched to Kotlin 1.9.24 per the Jetpack
        // Compose-to-Kotlin compatibility map. Kotlin 2.0+ would use the
        // org.jetbrains.kotlin.plugin.compose Gradle plugin instead of this.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":bluetooth"))

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Wear-specific Compose + Material3, and Horologist for round-screen scaffolding.
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.wear.compose:compose-navigation:1.3.1")
    implementation("com.google.android.horologist:horologist-compose-layout:0.6.11")

    // Tiles (ProtoLayout) for the quick-glance ANC/battery tile.
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.tiles:tiles-material:1.4.0")
    implementation("androidx.wear.protolayout:protolayout:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.0")
    implementation("androidx.wear:wear-tooling-preview:1.0.0")

    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

plugins {
    id("com.android.application")
    // No org.jetbrains.kotlin.android plugin: AGP 9's built-in Kotlin
    // collides with it. See bluetooth/build.gradle.kts for the full note.
}

android {
    namespace = "com.nothingx.phone"
    compileSdk = 36 // see wear/build.gradle.kts — stepped back from 37,
    // CI's sdkmanager can't resolve that platform package yet

    defaultConfig {
        applicationId = "com.nothingx.phone"
        // Must be >= :bluetooth's minSdk (30, set for Wear OS 3) since phone
        // depends on it — the manifest merger rejects a lower minSdk than a
        // dependency declares. 30 is still a perfectly normal phone minSdk
        // (Android 11+).
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Same fixed debug key as wear/build.gradle.kts — see its comment for why.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // No kotlinOptions{} block: built-in Kotlin's jvmTarget defaults to
    // compileOptions.targetCompatibility above (17) automatically.
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":bluetooth"))
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}

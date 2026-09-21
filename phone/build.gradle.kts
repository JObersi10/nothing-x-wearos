plugins {
    id("com.android.application")
    // No org.jetbrains.kotlin.android plugin: AGP 9's built-in Kotlin
    // collides with it. See bluetooth/build.gradle.kts for the full note.
}

android {
    namespace = "com.nothingx.phone"
    compileSdk = 37 // see settings.gradle.kts for the platforms;android-37.0 story

    defaultConfig {
        applicationId = "com.nothingx.phone"
        // Must be >= :bluetooth's minSdk (30, set for Wear OS 3) since phone
        // depends on it — the manifest merger rejects a lower minSdk than a
        // dependency declares. 30 is still a perfectly normal phone minSdk
        // (Android 11+).
        minSdk = 30
        targetSdk = 37
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
    // PhoneRelayService's CoroutineScope/launch — :bluetooth already depends
    // on this but as `implementation`, so it isn't visible here transitively.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
}

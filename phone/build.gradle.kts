plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nothingx.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nothingx.phone"
        // Must be >= :bluetooth's minSdk (30, set for Wear OS 3) since phone
        // depends on it — the manifest merger rejects a lower minSdk than a
        // dependency declares. 30 is still a perfectly normal phone minSdk
        // (Android 11+).
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":bluetooth"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}

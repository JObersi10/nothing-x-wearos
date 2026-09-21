plugins {
    id("com.android.library")
    // No org.jetbrains.kotlin.android plugin here: AGP 9's built-in Kotlin
    // (default since 9.0) registers the `kotlin` project extension itself —
    // applying kotlin-android on top collides with it ("Cannot add
    // extension with name 'kotlin', as there is an extension already
    // registered with that name"). See settings.gradle.kts for the full
    // AGP 9 migration note.
}

android {
    namespace = "com.nothingx.bluetooth"
    compileSdk = 36 // see settings.gradle.kts: compileSdk 37 (Android 17)
    // isn't resolvable by this CI runner's sdkmanager feed yet — stepped
    // back to 36 (Android 16, confirmed available) to unblock CI; bump
    // back to 37 once that's confirmed fetchable.

    defaultConfig {
        minSdk = 30 // Wear OS 3 baseline (Galaxy Watch 4 and newer)
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation(kotlin("test"))
}

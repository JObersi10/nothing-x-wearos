plugins {
    id("com.android.application")
    // No org.jetbrains.kotlin.android plugin: AGP 9's built-in Kotlin
    // (default since AGP 9.0) already registers the `kotlin` extension —
    // applying kotlin-android too collides with it. See
    // settings.gradle.kts and bluetooth/build.gradle.kts for the full note.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nothingx.wear"
    // Wear Widgets need compileSdk 37, but CI's sdkmanager couldn't
    // resolve `platforms;android-37` from Google's repository feed
    // (confirmed via the actual CI failure, not guessed) — stepped back to
    // 36 to get the toolchain migration itself green; see settings.gradle.kts
    // and CLAUDE.md for the full story. Bump back to 37 (and re-add Wear
    // Widget-specific work) once 37 is confirmed actually fetchable here.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nothingx.wear"
        minSdk = 30 // Wear OS 3 (Galaxy Watch 4 and newer) — the app itself still
        // supports this; Wear Widgets specifically need a newer on-device
        // renderer and simply won't show on older Wear OS 3 watches. The
        // full-screen Tile (NothingXTileService) stays as the fallback for
        // those, per both Google's own migration guidance and the user's
        // explicit ask to keep it for older watches.
        targetSdk = 36
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
    // No composeOptions{} block anymore: on Kotlin 2.0+ the Compose compiler
    // version comes from the org.jetbrains.kotlin.plugin.compose Gradle
    // plugin (applied above) instead — using both at once is the trap this
    // used to warn about.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // No kotlinOptions{} block: built-in Kotlin's jvmTarget defaults to
    // compileOptions.targetCompatibility above (17) automatically.

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":bluetooth"))

    // Bumped 2026-09-21 for the AGP 9 / Kotlin 2.2.10 toolchain jump (Wear
    // Widgets need compileSdk 37, which needs AGP 9.1+, which needs KGP
    // 2.2.10+ — see settings.gradle.kts). Compose library versions
    // deliberately pinned BELOW the versions that themselves require
    // compileSdk 37 (compose-ui 1.12.0+, shipped in compose-bom 2026.04.00+)
    // since compileSdk is stepped back to 36 here (platforms;android-37
    // isn't resolvable by this CI environment's sdkmanager yet — see the
    // compileSdk comment above and CLAUDE.md). compose-bom 2025.12.01 is
    // the last BOM release still on compose-ui 1.11.x; wear-compose 1.5.6
    // (Dec 2025) predates wear-compose's own jump to a compileSdk-37-only
    // compose-ui floor. Both are still well within Kotlin 2.2.10's Compose
    // compiler compatibility window (the compiler-runtime version check
    // cares about a much lower floor than this). Revisit once compileSdk
    // 37 is confirmed actually fetchable in CI — see the Phase 2 blocker
    // note in HANDOFF.md.
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Wear-specific Compose + Material3, and Horologist for round-screen scaffolding.
    implementation("androidx.wear.compose:compose-material:1.5.6")
    implementation("androidx.wear.compose:compose-foundation:1.5.6")
    implementation("androidx.wear.compose:compose-navigation:1.5.6")
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

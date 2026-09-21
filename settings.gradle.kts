pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    // Centralizing versions here (rather than in each module's own `plugins {}`
    // block) is what stops "Kotlin Gradle plugin was loaded multiple times in
    // different subprojects" — Gradle warns about that today and Gradle 9 makes
    // it a hard error. Root build.gradle.kts still declares none of these
    // itself (see its own comment) — that's what lets `:protocol:test` build
    // standalone in network-restricted sandboxes without ever resolving AGP.
    //
    // AGP 9.1.1 / Kotlin 2.2.10 (bumped 2026-09-21 for Wear Widgets, which
    // require compileSdk 37 — AGP 9.1.0+ is the minimum that supports it).
    // This forced the Kotlin 1.9->2.x jump too (AGP 9 requires KGP 2.2.10+),
    // which is also why wear/build.gradle.kts now uses the
    // org.jetbrains.kotlin.plugin.compose Gradle plugin instead of
    // composeOptions{} — exactly the contingency this comment used to warn
    // about ("if Kotlin ever gets bumped to 2.0+, switch to the plugin").
    //
    // No org.jetbrains.kotlin.android entry: AGP 9.0+ ships "built-in
    // Kotlin" and applies it by default, registering the `kotlin` project
    // extension itself. Explicitly applying kotlin-android on top (as this
    // project did through AGP 8) collides with that ("Cannot add extension
    // with name 'kotlin', as there is an extension already registered with
    // that name") — confirmed as the exact CI failure on the first Phase 1
    // push. Fix is to stop applying kotlin-android at all (not to opt out
    // via android.builtInKotlin=false in gradle.properties) since that
    // opt-out is explicitly temporary and goes away entirely in AGP 10 — no
    // reason to build on a flag with a known expiry. bluetooth/wear/phone's
    // build.gradle.kts no longer apply org.jetbrains.kotlin.android; their
    // kotlinOptions{} blocks were dropped too since built-in Kotlin's
    // jvmTarget defaults to android.compileOptions.targetCompatibility.
    // org.jetbrains.kotlin.jvm stays — :protocol is pure JVM, never touches
    // AGP, and built-in Kotlin doesn't apply to it at all.
    //
    // compileSdk/targetSdk in the Android modules are 37 — this took three
    // rounds to actually land, worth recording so nobody "fixes" it back
    // down again on a stale assumption:
    //   1. `platforms;android-37` (no minor version) failed sdkmanager with
    //      "Failed to find package" — looked at the time like Android 17/
    //      API 37 just wasn't resolvable in CI yet, so compileSdk got
    //      stepped back to 36 (and compose-bom/wear-compose down to
    //      versions that don't need 37) to get the toolchain bump green on
    //      its own merits.
    //   2. A diagnostic CI step (`sdkmanager --list`, since removed once it
    //      had answered the question) showed the real package id: this
    //      Android release cycle versions the platform itself with a minor
    //      number, same idea as `-ext14` extension levels but for the
    //      platform baseline — `platforms;android-37.0`, `37.1`, `37.2` all
    //      exist as distinct packages, alongside `build-tools;37.0.0`.
    //      `platforms;android-37` (bare) was never going to resolve; it was
    //      never the package's real name.
    //   3. CI step fixed to install `platforms;android-37.0` and
    //      `build-tools;37.0.0` — AGP's `compileSdk = 37` (a plain Int)
    //      resolves to that `.0` baseline. Confirmed green; compileSdk 37
    //      and the Compose versions that need it (compose-bom 2026.09.00,
    //      wear-compose 1.6.2) are back.
    plugins {
        id("com.android.application") version "9.1.1"
        id("com.android.library") version "9.1.1"
        id("org.jetbrains.kotlin.jvm") version "2.2.10"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nothing-x-wearos"

include(":protocol")
include(":bluetooth")
include(":wear")
include(":phone")

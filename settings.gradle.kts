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
    // compileSdk/targetSdk in the Android modules are 36, NOT 37, even
    // though Wear Widgets need 37 (the whole reason for this toolchain
    // bump). Confirmed via the actual CI failure (not a guess): after
    // fixing the built-in-Kotlin collision above, the very next run failed
    // with `Warning: Failed to find package 'platforms;android-37'` —
    // Google's SDK repository feed, as seen by this CI runner's
    // sdkmanager, does not yet serve that platform package, whatever its
    // real-world release status. AGP 9.1.1/Kotlin 2.2.10 themselves don't
    // require compileSdk 37 — that's purely a Wear Widget library
    // requirement — so this toolchain bump still stands on its own at
    // compileSdk 36. Wear Widget implementation work is blocked until
    // `platforms;android-37` is confirmed actually resolvable in CI; don't
    // bump compileSdk back to 37 speculatively, verify the package
    // resolves first.
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

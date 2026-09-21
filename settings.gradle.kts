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
    plugins {
        id("com.android.application") version "9.1.1"
        id("com.android.library") version "9.1.1"
        id("org.jetbrains.kotlin.android") version "2.2.10"
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

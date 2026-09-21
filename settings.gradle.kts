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
    plugins {
        id("com.android.application") version "8.5.2"
        id("com.android.library") version "8.5.2"
        id("org.jetbrains.kotlin.android") version "1.9.24"
        id("org.jetbrains.kotlin.jvm") version "1.9.24"
        id("org.jetbrains.kotlin.plugin.compose") version "1.9.24"
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

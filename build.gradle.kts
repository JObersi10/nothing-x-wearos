// Intentionally no root-level `plugins {}` declarations for the Android Gradle
// Plugin here. Each Android module (wear/, phone/, bluetooth/) declares its own
// plugin + version in its own build.gradle.kts. Keeping that out of this root
// script means `./gradlew :protocol:test` never needs to resolve AGP — useful
// in sandboxes where dl.google.com is network-blocked but plugins.gradle.org
// and Maven Central are reachable (pure-Kotlin modules still build and test).

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

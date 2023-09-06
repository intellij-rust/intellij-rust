@file:Suppress("UnstableApiUsage")

rootProject.name = "intellij-rust"

pluginManagement {
    includeBuild("build-tools/build-plugins")
    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            mavenContent { snapshotsOnly() }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
// I'd like to define all repositories here, but the IntelliJ Gradle Plugin adds custom repositories,
// which breaks these definitions.
//    repositories {
//        mavenCentral()
//        maven("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2")
//        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
//    }
}

include(
    ":plugin", // Special module with run, build and publish tasks

    ":clion",
    ":copyright",
    ":coverage",
    ":debugger",
    ":duplicates",
    ":grammar-kit-fake-psi-deps",
    ":grazie",
    ":idea",
    ":intelliLang",
    ":js",
    ":ml-completion",
    ":profiler",

    // proposed restructuring, so that all the subprojects are grouped together and are sorted with a descriptive prefix
//    "ijrust-components:grammar-kit-fake-psi-deps",
//    "ijrust-components:ide-clion",
//    "ijrust-components:ide-idea",
//    "ijrust-components:integration-copyright",
//    "ijrust-components:integration-coverage",
//    "ijrust-components:integration-debugger",
//    "ijrust-components:integration-duplicates",
//    "ijrust-components:integration-grazie",
//    "ijrust-components:integration-intellilang",
//    "ijrust-components:integration-ml-completion",
//    "ijrust-components:interop-js",
//    "ijrust-components:plugin",
//    "ijrust-components:profiler",
)

// Configure Gradle Build Cache. It is enabled in `gradle.properties` via `org.gradle.caching`.
buildCache {
    local {
        isEnabled = System.getenv("CI") == null
        directory = File(rootDir, "build/build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
//enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

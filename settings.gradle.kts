rootProject.name = "intellij-rust"
// Special module with run, build and publish tasks
include("plugin")
include(
    "common",
    "idea",
    "clion",
    "debugger",
    "toml",
    "copyright",
    "coverage",
    "intelliLang",
    "duplicates",
    "grazie",
    "ml-completion",
    "intellij-toml",
    "intellij-toml:core"
)

val platformVersion: String by extra

if (platformVersion.toInt() < 212) {
    include("js")
}

// Configure Gradle Build Cache. It is enabled in `gradle.properties` via `org.gradle.caching`.
// Also, `gradle clean` task is configured to delete `build-cache` directory.
buildCache {
    local {
        isEnabled = System.getenv("CI") == null
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

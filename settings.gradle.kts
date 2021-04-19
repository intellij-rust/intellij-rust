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
    "js",
    "ml-completion",
    "intellij-toml"
)

// Configure Gradle Build Cache. It is enabled in `gradle.properties` via `org.gradle.caching`.
// Also, `gradle clean` task is configured to delete `build-cache` directory.
buildCache {
    // The local cache is stored in `build-cache` directory and is enabled on developer's machines
    local {
        isEnabled = System.getenv("CI") == null
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 30
    }

    // The remote cache is enabled on CI only

    val remoteUrl = System.getenv("GRADLE_REMOTE_CACHE_URL")
    val remoteUsername = System.getenv("GRADLE_REMOTE_CACHE_USERNAME")
    val remotePassword = System.getenv("GRADLE_REMOTE_CACHE_PASSWORD")

    if (remoteUrl != null && remoteUsername != null && remotePassword != null) {
        remote<HttpBuildCache> {
            isEnabled = true
            isPush = System.getenv("CI") != null
            url = uri(remoteUrl)
            credentials {
                username = remoteUsername
                password = remotePassword
            }
        }
    }
}

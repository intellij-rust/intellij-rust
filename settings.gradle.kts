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

pluginManagement {
    repositories {
        maven("https://jetbrains.bintray.com/intellij-plugin-service")
        maven("https://plugins.gradle.org/m2/")
        mavenCentral()
    }
}

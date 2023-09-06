plugins {
    id("intellij_rust.conventions.intellij")
}

description = "integration with IntelliJ Copyright plugin"

intellij {
    version.set(intellijRust.ideaVersion)
    plugins.set(listOf(intellijRust.copyrightPlugin))
}

dependencies {
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

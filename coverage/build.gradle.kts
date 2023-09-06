plugins {
    id("intellij_rust.conventions.intellij")
}

description = "integration with IntelliJ Coverage plugin"

dependencies {
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

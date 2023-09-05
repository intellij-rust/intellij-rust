plugins {
    id("intellij_rust.conventions.intellij")
}

description = "support `Duplicated code fragment` inspection"

dependencies {
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

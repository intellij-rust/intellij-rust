plugins {
    id("intellij_rust.conventions.intellij")
}

description = "integration with Grazie plugin"

intellij {
    plugins.set(listOf(intellijRust.graziePlugin))
}

dependencies {
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

plugins {
    id("intellij_rust.conventions.intellij")
}

intellij {
    version.set(intellijRust.clionVersion)
    plugins.set(intellijRust.clionPlugins)
}
dependencies {
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

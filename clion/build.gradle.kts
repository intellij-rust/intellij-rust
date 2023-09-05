plugins {
    id("intellij_rust.conventions.intellij")
}

description = "contains code available only in CLion"

intellij {
    version.set(intellijRust.clionVersion)
    plugins.set(intellijRust.clionPlugins)
}

dependencies {
    implementation(projects.intellijRust)
    implementation(projects.debugger)
    testImplementation(testFixtures(projects.intellijRust))
}

plugins {
    id("intellij_rust.conventions.intellij")
}

description = "interop with JavaScript language"

intellij {
    plugins.set(listOf(intellijRust.javaScriptPlugin))
}

dependencies {
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

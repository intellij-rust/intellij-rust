plugins {
    id("intellij_rust.conventions.intellij")
}

description = "integration with IntelliLang"

intellij {
    plugins.set(listOf(intellijRust.intelliLangPlugin))
}

dependencies {
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

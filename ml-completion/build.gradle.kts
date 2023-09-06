plugins {
    id("intellij_rust.conventions.intellij")
}

description = "integration with Machine Learning Code Completion plugin"

intellij {
    plugins.set(listOf(intellijRust.mlCompletionPlugin))
}

dependencies {
    implementation(libs.intellijCompletionRanking.rust)
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

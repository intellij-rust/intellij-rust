plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.intelliJGradle)
    implementation(libs.gradlePlugin.intelliJGradleGrammarKit)
    implementation(libs.gradlePlugin.testRetry)

    implementation(libs.jsoup)
}

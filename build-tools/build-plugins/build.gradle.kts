plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.intelliJGradle)
    implementation(libs.gradlePlugin.intelliJGradleGrammarKit)
    implementation(libs.gradlePlugin.gradleProperties)
    implementation(libs.gradlePlugin.testRetry)

    implementation("org.jsoup:jsoup:1.16.1")
}

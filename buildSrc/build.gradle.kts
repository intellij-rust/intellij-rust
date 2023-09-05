plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
    implementation("org.jetbrains.intellij.plugins:gradle-intellij-plugin:1.13.1")
    implementation("org.jetbrains.intellij.plugins:gradle-grammarkit-plugin:2022.3.1")
    implementation("net.saliman:gradle-properties-plugin:1.5.2")
    implementation("org.gradle:test-retry-gradle-plugin:1.5.4")
}

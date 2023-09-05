plugins {
    ijrust.conventions.base
    idea
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.grammarkit")
    id("net.saliman.properties")
    id("org.gradle.test-retry")
}

intellij {
    version.set(ijRustBuild.clionVersion)
    plugins.set(ijRustBuild.clionPlugins)
}

dependencies {
    implementation(project(":"))
    implementation(project(":debugger"))
    testImplementation(project(":", "testOutput"))
}

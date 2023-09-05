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
    version.set(ijRustBuild.ideaVersion)
    plugins.set(listOf(
        ijRustBuild.javaPlugin,
        // this plugin registers `com.intellij.ide.projectView.impl.ProjectViewPane` for IDEA that we use in tests
        ijRustBuild.javaIdePlugin
    ))
}

dependencies {
    implementation(project(":"))
    testImplementation(project(":", "testOutput"))
}

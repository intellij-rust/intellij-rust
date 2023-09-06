plugins {
    id("intellij_rust.conventions.intellij")
}

description = "contains code available only in IDEA"

intellij {
    version.set(intellijRust.ideaVersion)
    plugins.set(
        listOf(
            intellijRust.javaPlugin,
            // this plugin registers `com.intellij.ide.projectView.impl.ProjectViewPane` for IDEA that we use in tests
            intellijRust.javaIdePlugin
        )
    )
}

dependencies {
    implementation(projects.intellijRust)
    testImplementation(testFixtures(projects.intellijRust))
}

plugins {
    ijrust.conventions.intellij
}

intellij {
    version.set(ijRustBuild.ideaVersion)
    plugins.set(listOf(ijRustBuild.copyrightPlugin))
}
dependencies {
    implementation(project(":"))
    testImplementation(project(":", "testOutput"))
}

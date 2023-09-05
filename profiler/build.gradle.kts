plugins {
    ijrust.conventions.intellij
}

intellij {
    version.set(ijRustBuild.clionVersion)
    plugins.set(ijRustBuild.clionPlugins)
}

dependencies {
    implementation(project(":"))
    testImplementation(project(":", "testOutput"))
}

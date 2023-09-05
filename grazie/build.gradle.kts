plugins {
    ijrust.conventions.intellij
}

intellij {
    plugins.set(listOf(ijRustBuild.graziePlugin))
}

dependencies {
    implementation(project(":"))
    testImplementation(project(":", "testOutput"))
}

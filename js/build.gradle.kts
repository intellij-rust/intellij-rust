plugins {
    ijrust.conventions.intellij
}

intellij {
    plugins.set(listOf(ijRustBuild.javaScriptPlugin))
}

dependencies {
    implementation(project(":"))
    testImplementation(project(":", "testOutput"))
}

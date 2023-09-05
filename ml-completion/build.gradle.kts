plugins {
    ijrust.conventions.intellij
}

intellij {
    plugins.set(listOf(ijRustBuild.mlCompletionPlugin))
}

dependencies {
    implementation("org.jetbrains.intellij.deps.completion:completion-ranking-rust:0.4.1")
    implementation(project(":"))
    testImplementation(project(":", "testOutput"))
}

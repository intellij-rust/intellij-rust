plugins {
    ijrust.conventions.intellij
}

intellij {
    plugins.set(listOf(ijRustBuild.intelliLangPlugin))
}
dependencies {
    implementation(project(":"))
    testImplementation(project(":", "testOutput"))
}

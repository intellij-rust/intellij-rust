plugins {
    ijrust.conventions.intellij
}

dependencies {
    implementation(project(":"))
    testImplementation(project(":", "testOutput"))
}

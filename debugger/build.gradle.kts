plugins {
    id("intellij_rust.conventions.intellij")
    antlr
}

description = "debugger related code"

intellij {
    if (intellijRust.baseIDE.get() == "idea") {
        plugins.set(intellijRust.nativeDebugPlugin.map { listOf(it) })
    } else {
        version.set(intellijRust.clionVersion)
        plugins.set(intellijRust.clionPlugins)
    }
}

dependencies {
    implementation(projects.intellijRust)
    implementation(libs.antlr.runtime)

    testImplementation(testFixtures(projects.intellijRust))

    antlr(libs.antlr.core)
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

tasks.generateGrammarSource {
    arguments.add("-no-listener")
    arguments.add("-visitor")
    outputDirectory = file("src/gen/org/rust/debugger/lang")
}

// Exclude antlr4 from transitive dependencies of `:debugger:api` configuration (https://github.com/gradle/gradle/issues/820)
configurations.api {
    setExtendsFrom(extendsFrom.filter { it.name != "antlr" })
}

plugins {
    ijrust.conventions.intellij
    id("antlr")
}

intellij {
    if (ijRustBuild.baseIDE.get() == "idea") {
        plugins.set(listOf(ijRustBuild.nativeDebugPlugin))
    } else {
        version.set(ijRustBuild.clionVersion)
        plugins.set(ijRustBuild.clionPlugins)
    }
}

// Kotlin Gradle support doesn't generate proper extensions if the plugin is not declared in `plugin` block.
// But if we do it, `antlr` plugin will be applied to root project as well that we want to avoid.
// So, let's define all necessary things manually
val antlr by configurations
val generateGrammarSource: AntlrTask by tasks
val generateTestGrammarSource: AntlrTask by tasks

dependencies {
    implementation(project(":"))
    antlr("org.antlr:antlr4:4.13.0")
    implementation("org.antlr:antlr4-runtime:4.13.0")
    testImplementation(project(":", "testOutput"))
}
tasks {
    compileKotlin {
        dependsOn(generateGrammarSource)
    }
    compileTestKotlin {
        dependsOn(generateTestGrammarSource)
    }

    generateGrammarSource {
        arguments.add("-no-listener")
        arguments.add("-visitor")
        outputDirectory = file("src/gen/org/rust/debugger/lang")
    }
}
// Exclude antlr4 from transitive dependencies of `:debugger:api` configuration (https://github.com/gradle/gradle/issues/820)
configurations.api {
    setExtendsFrom(extendsFrom.filter { it.name != "antlr" })
}

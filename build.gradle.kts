import groovy.xml.XmlParser
import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jsoup.Jsoup
import java.io.Writer
import kotlin.concurrent.thread
import ijrust.utils.not

//
//val isCI = System.getenv("CI") != null
//val isTeamcity = System.getenv("TEAMCITY_VERSION") != null
//
//val channel = prop("publishChannel")
//val platformVersion = prop("platformVersion").toInt()
//val baseIDE = prop("baseIDE")
//val ideaVersion = prop("ideaVersion")
//val clionVersion = prop("clionVersion")
//val baseVersion = when (baseIDE) {
//    "idea" -> ideaVersion
//    "clion" -> clionVersion
//    else -> error("Unexpected IDE name: `$baseIDE`")
//}
//
//val tomlPlugin = "org.toml.lang"
//val nativeDebugPlugin: String by project
//val graziePlugin = "tanvd.grazi"
//val psiViewerPlugin: String by project
//val intelliLangPlugin = "org.intellij.intelliLang"
//val copyrightPlugin = "com.intellij.copyright"
//val javaPlugin = "com.intellij.java"
//val javaIdePlugin = "com.intellij.java.ide"
//val javaScriptPlugin = "JavaScript"
//val clionPlugins = listOf("com.intellij.cidr.base", "com.intellij.clion")
//val mlCompletionPlugin = "com.intellij.completion.ml.ranking"
//
//val compileNativeCodeTaskName = "compileNativeCode"
//
//val grammarKitFakePsiDeps = "grammar-kit-fake-psi-deps"
//
//val basePluginArchiveName = "intellij-rust"

plugins {
    ijrust.conventions.intellij
    id("net.saliman.properties")
}

idea {
    module {
        // https://github.com/gradle/kotlin-dsl/issues/537/
        excludeDirs = excludeDirs + file("testData") + file("deps") + file("bin") +
            file("${ijRustBuild.grammarKitFakePsiDeps}/src/main/kotlin")
    }
}

allprojects {
    apply {
        plugin("idea")
        plugin("kotlin")
        plugin("org.jetbrains.grammarkit")
        plugin("org.jetbrains.intellij")
        plugin("org.gradle.test-retry")
    }



    val testOutput = configurations.create("testOutput")

    dependencies {
        compileOnly(kotlin("stdlib-jdk8"))
        testOutput(sourceSets.getByName("test").output.classesDirs)
    }

    afterEvaluate {
        tasks.withType<AbstractTestTask> {
            testLogging {
                if (hasProp("showTestStatus") && prop("showTestStatus").toBoolean()) {
                    events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                }
                exceptionFormat = TestExceptionFormat.FULL
            }
        }

        tasks.withType<Test>().configureEach {
            jvmArgs = listOf("-Xmx2g", "-XX:-OmitStackTraceInFastThrow")

            // We need to prevent the platform-specific shared JNA library to loading from the system library paths,
            // because otherwise it can lead to compatibility issues.
            // Also note that IDEA does the same thing at startup, and not only for tests.
            systemProperty("jna.nosys", "true")

            // The factory should be set up automatically in `IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool`,
            // but when tests are launched by Gradle this may not happen because Gradle can use the pool earlier.
            // Setting this factory is critical for `ReadMostlyRWLock` performance, so ensure it is properly set
            systemProperty(
                "java.util.concurrent.ForkJoinPool.common.threadFactory",
                "com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory"
            )
            if (ijRustBuild.isTeamcity.get()) {
                // Make teamcity builds green if only muted tests fail
                // https://youtrack.jetbrains.com/issue/TW-16784
                ignoreFailures = true
            }
            if (hasProp("excludeTests")) {
                exclude(prop("excludeTests"))
            }
        }
    }
}


val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        // If cache path doesn't exist, we need to create it manually
        // because otherwise gradle-intellij-plugin will ignore it
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }

//val pluginProjects: List<Project>
//    get() = rootProject.allprojects.filter { it.name != grammarKitFakePsiDeps }


////
//project(":plugin") {
//}

//project(":$grammarKitFakePsiDeps")

//project(":") {
intellij {
    plugins.set(listOf(ijRustBuild.tomlPlugin))
}

sourceSets {
    main {
        if (ijRustBuild.channel.get() == "nightly" || ijRustBuild.channel.get() == "dev") {
            resources.srcDirs("src/main/resources-nightly")
            resources.srcDirs("src/${ijRustBuild.platformVersion.get()}/main/resources-nightly")
        } else {
            resources.srcDirs("src/main/resources-stable")
            resources.srcDirs("src/${ijRustBuild.platformVersion.get()}/main/resources-stable")
        }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.14.2") {
        exclude(module = "jackson-core")
        exclude(module = "jackson-databind")
        exclude(module = "jackson-annotations")
    }
    api("io.github.z4kn4fein:semver:1.4.2") {
        excludeKotlinDeps()
    }
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.5.0.202303070854-r") {
        exclude("org.slf4j")
    }
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
}

tasks {
    generateLexer {
        sourceFile.set(file("src/main/grammars/RustLexer.flex"))
        targetDir.set("src/gen/org/rust/lang/core/lexer")
        targetClass.set("_RustLexer")
        purgeOldFiles.set(true)
    }
    generateParser {
        sourceFile.set(file("src/main/grammars/RustParser.bnf"))
        targetRoot.set("src/gen")
        pathToParser.set("org/rust/lang/core/parser/RustParser.java")
        pathToPsiRoot.set("org/rust/lang/core/psi")
        purgeOldFiles.set(true)
        classpath(project(":${ijRustBuild.grammarKitFakePsiDeps}").sourceSets.main.get().runtimeClasspath)
    }
    withType<KotlinCompile> {
        dependsOn(generateLexer, generateParser)
    }

    // In tests `resources` directory is used instead of `sandbox`
    processTestResources {
        dependsOn(named(ijRustBuild.compileNativeCodeTaskName))
        from("${rootDir}/bin") {
            into("bin")
            include("**")
        }
    }
}

task("resolveDependencies") {
    doLast {
        rootProject.allprojects
            .map { it.configurations }
            .flatMap { it.filter { c -> c.isCanBeResolved } }
            .forEach { it.resolve() }
    }
}
//}



task("updateCargoOptions") {
    doLast {
        val file = File("src/main/kotlin/org/rust/cargo/util/CargoOptions.kt")
        file.bufferedWriter().use {
            it.writeln(
                """
                /*
                 * Use of this source code is governed by the MIT license that can be
                 * found in the LICENSE file.
                 */

                package org.rust.cargo.util

                data class CargoOption(val name: String, val description: String) {
                    val longName: String get() = "--${'$'}name"
                }

            """.trimIndent()
            )
            it.writeCargoOptions("https://doc.rust-lang.org/cargo/commands")
        }
    }
}


fun Writer.writeCargoOptions(baseUrl: String) {

    data class CargoOption(
        val name: String,
        val description: String
    )

    data class CargoCommand(
        val name: String,
        val description: String,
        val options: List<CargoOption>
    )

    fun fetchCommand(commandUrl: String): CargoCommand {
        val document = Jsoup.connect("$baseUrl/$commandUrl").get()

        val fullCommandDesc = document.select("div[class=sectionbody] > p").text()
        val parts = fullCommandDesc.split(" - ", limit = 2)
        check(parts.size == 2) { "Invalid page format: $baseUrl/$commandUrl$" }
        val commandName = parts.first().removePrefix("cargo-")
        val commandDesc = parts.last()

        val options = document
            .select("dt > strong:matches(^--)")
            .map { option ->
                val optionName = option.text().removePrefix("--")
                val nextSiblings = generateSequence(option.parent()) { it.nextElementSibling() }
                val descElement = nextSiblings.first { it.tagName() == "dd" }
                val fullOptionDesc = descElement.select("p").text()
                val optionDesc = fullOptionDesc.substringBefore(". ").removeSuffix(".")
                CargoOption(optionName, optionDesc)
            }

        return CargoCommand(commandName, commandDesc, options)
    }

    fun fetchCommands(): List<CargoCommand> {
        val document = Jsoup.connect("$baseUrl/cargo.html").get()
        val urls = document.select("dt > a[href]").map { it.attr("href") }
        return urls.map { fetchCommand(it) }
    }

    fun writeEnumVariant(command: CargoCommand, isLast: Boolean) {
        val variantName = command.name.toUpperCase().replace('-', '_')
        val renderedOptions = command.options.joinToString(
            separator = ",\n            ",
            prefix = "\n            ",
            postfix = "\n        "
        ) { "CargoOption(\"${it.name}\", \"\"\"${it.description}\"\"\")" }

        writeln(
            """
        |    $variantName(
        |        description = "${command.description}",
        |        options = ${if (command.options.isEmpty()) "emptyList()" else "listOf($renderedOptions)"}
        |    )${if (isLast) ";" else ","}
        """.trimMargin()
        )
        writeln()
    }

    val commands = fetchCommands()
    writeln("enum class CargoCommands(val description: String, val options: List<CargoOption>) {")
    for ((index, command) in commands.withIndex()) {
        writeEnumVariant(command, isLast = index == commands.size - 1)
    }
    writeln("    val presentableName: String get() = name.toLowerCase().replace('_', '-')")
    writeln("}")
}

fun Writer.writeln(str: String = "") {
    write(str)
    write("\n")
}

fun hasProp(name: String): Boolean = extra.has(name)

fun prop(name: String): String =
    extra.properties[name] as? String
        ?: error("Property `$name` is not defined in gradle.properties")


inline operator fun <T : Task> T.invoke(a: T.() -> Unit): T = apply(a)

fun String.execute(wd: String? = null, ignoreExitCode: Boolean = false, print: Boolean = true): String =
    split(" ").execute(wd, ignoreExitCode, print)

fun List<String>.execute(wd: String? = null, ignoreExitCode: Boolean = false, print: Boolean = true): String {
    val process = ProcessBuilder(this)
        .also { pb -> wd?.let { pb.directory(File(it)) } }
        .start()
    var result = ""
    val errReader = thread { process.errorStream.bufferedReader().forEachLine { println(it) } }
    val outReader = thread {
        process.inputStream.bufferedReader().forEachLine { line ->
            if (print) {
                println(line)
            }
            result += line
        }
    }
    process.waitFor()
    outReader.join()
    errReader.join()
    if (process.exitValue() != 0 && !ignoreExitCode) error("Non-zero exit status for `$this`")
    return result
}



fun <T : ModuleDependency> T.excludeKotlinDeps() {
    exclude(module = "kotlin-reflect")
    exclude(module = "kotlin-runtime")
    exclude(module = "kotlin-stdlib")
    exclude(module = "kotlin-stdlib-common")
    exclude(module = "kotlin-stdlib-jdk8")
    exclude(module = "kotlinx-serialization-core")
}

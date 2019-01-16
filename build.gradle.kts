import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.SourceSet
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.tasks.Jar
import java.io.Writer
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import kotlin.concurrent.thread

val CI = System.getenv("CI") != null

val channel = prop("publishChannel")
val platformVersion = prop("platformVersion")

val excludedJars = listOf(
    "java-api.jar",
    "java-impl.jar"
)

plugins {
    idea
    kotlin("jvm") version "1.3.11"
    id("org.jetbrains.intellij") version "0.3.12"
    id("org.jetbrains.grammarkit") version "2018.2.2"
    id("de.undercouch.download") version "3.4.3"
    id("net.saliman.properties") version "1.4.6"
}

idea {
    module {
        // https://github.com/gradle/kotlin-dsl/issues/537/
        excludeDirs = excludeDirs + file("testData") + file("deps")
    }
}

allprojects {
    apply {
        plugin("idea")
        plugin("kotlin")
        plugin("org.jetbrains.grammarkit")
        plugin("org.jetbrains.intellij")
    }

    repositories {
        mavenCentral()
        maven { setUrl("https://dl.bintray.com/jetbrains/markdown") }
    }

    idea {
        module {
            generatedSourceDirs.add(file("src/gen"))
        }
    }

    intellij {
        version = prop("ideaVersion")
        downloadSources = !CI
        updateSinceUntilBuild = true
        instrumentCode = false
        ideaDependencyCachePath = file("deps").absolutePath

        tasks.withType<PatchPluginXmlTask> {
            sinceBuild(prop("sinceBuild"))
            untilBuild(prop("untilBuild"))
        }
    }

    tasks.withType<PublishTask> {
        username(prop("publishUsername"))
        password(prop("publishPassword"))
        channels(channel)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            languageVersion = "1.2"
            apiVersion = "1.2"
            freeCompilerArgs = listOf("-Xjvm-default=enable")
        }
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = VERSION_1_8
        targetCompatibility = VERSION_1_8
    }

    sourceSets {
        getByName("main").apply {
            java.srcDirs("src/gen")
            kotlin.srcDirs("src/$platformVersion/main/kotlin")
            resources.srcDirs("src/$platformVersion/main/resources")
        }
        getByName("test").apply {
            kotlin.srcDirs("src/$platformVersion/test/kotlin")
            resources.srcDirs("src/$platformVersion/test/resources")
        }
    }

    afterEvaluate {
        val mainSourceSet = sourceSets.getByName("main")
        val mainClassPath = mainSourceSet.compileClasspath
        val exclusion = mainClassPath.filter { it.name in excludedJars }
        mainSourceSet.compileClasspath = mainClassPath - exclusion

        tasks.withType<AbstractTestTask> {
            testLogging {
                if (hasProp("showTestStatus") && prop("showTestStatus").toBoolean()) {
                    events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                    exceptionFormat = TestExceptionFormat.FULL
                }
            }
        }

        // We need to prevent the platform-specific shared JNA library to loading from the system library paths,
        // because otherwise it can lead to compatibility issues.
        // Also note that IDEA does the same thing at startup, and not only for tests.
        tasks.withType<Test>().configureEach {
            systemProperty("jna.nosys", "true")
        }
    }
}

val channelSuffix = if (channel.isBlank()) "" else "-$channel"
val clionVersion = prop("clionVersion")
val clionFullName = if (CI) "clion" else "clion-$clionVersion"

val rustProjects = rootProject.subprojects.filter { it.name != "intellij-toml" }

project(":") {
    val versionSuffix = "-$platformVersion$channelSuffix"
    version = "0.2.0.${prop("buildNumber")}$versionSuffix"
    intellij {
        pluginName = "intellij-rust"
//        alternativeIdePath = "deps/clion-$clionVersion"
        setPlugins(project(":intellij-toml"), "IntelliLang", "copyright")
    }

    val testOutput = configurations.create("testOutput")

    dependencies {
        compile("org.jetbrains:markdown:0.1.30") {
            exclude(module = "kotlin-runtime")
            exclude(module = "kotlin-stdlib")
        }
        testOutput(sourceSets.getByName("test").output)
    }

    val generateRustLexer = task<GenerateLexer>("generateRustLexer") {
        source = "src/main/grammars/RustLexer.flex"
        targetDir = "src/gen/org/rust/lang/core/lexer"
        targetClass = "_RustLexer"
        purgeOldFiles = true
    }

    val generateRustDocHighlightingLexer = task<GenerateLexer>("generateRustDocHighlightingLexer") {
        source = "src/main/grammars/RustDocHighlightingLexer.flex"
        targetDir = "src/gen/org/rust/lang/doc/lexer"
        targetClass = "_RustDocHighlightingLexer"
        purgeOldFiles = true
    }

    val generateRustParser = task<GenerateParser>("generateRustParser") {
        source = "src/main/grammars/RustParser.bnf"
        targetRoot = "src/gen"
        pathToParser = "/org/rust/lang/core/parser/RustParser.java"
        pathToPsiRoot = "/org/rust/lang/core/psi"
        purgeOldFiles = true
    }

    val downloadClion = task<Download>("downloadClion") {
        onlyIf { !file("${project.projectDir}/deps/clion-$clionVersion.tar.gz").exists() }
        src("https://download.jetbrains.com/cpp/CLion-$clionVersion.tar.gz")
        dest(file("${project.projectDir}/deps/clion-$clionVersion.tar.gz"))
    }

    val unpackClion = task<Copy>("unpackClion") {
        onlyIf { !file("${project.projectDir}/deps/$clionFullName").exists() }
        from(tarTree("deps/clion-$clionVersion.tar.gz"))
        into(file("${project.projectDir}/deps"))
        doLast {
            file("${project.projectDir}/deps/clion-$clionVersion").renameTo(file("${project.projectDir}/deps/$clionFullName"))
        }
        dependsOn(downloadClion)
    }

    tasks.withType<KotlinCompile> {
        dependsOn(
            generateRustLexer, generateRustDocHighlightingLexer,
            generateRustParser, unpackClion
        )
    }

    tasks.withType<Test> {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    task("resolveDependencies") {
        dependsOn(unpackClion)
        doLast {
            rootProject.allprojects
                .map { it.configurations }
                .flatMap { listOf(it.compile, it.testCompile) }
                .forEach { it.resolve() }
        }
    }

    tasks.withType<PrepareSandboxTask> {
        from("prettyPrinters") {
            into("${intellij.pluginName}/prettyPrinters")
            include("*.py")
        }
        for (rustProject in rustProjects) {
            val jar: Jar by rustProject.tasks
            from(jar.outputs.files) {
                into("${intellij.pluginName}/lib")
                include("*.jar")
            }
            dependsOn(jar)
        }
    }

    val copyXmls = task<Copy>("copyXmls") {
        val mainMetaInf = "${project.sourceSets.getByName("main").output.resourcesDir}/META-INF"
        for (rustProject in rustProjects) {
            from("${rustProject.projectDir}/src/main/resources/META-INF")
        }
        into(mainMetaInf)
        include("*.xml")
    }

    tasks.withType<Jar> {
        dependsOn(copyXmls)
    }
}

project(":idea") {
    dependencies {
        compile(project(":"))
        testCompile(project(":", "testOutput"))
    }
}

project(":debugger") {
    dependencies {
        compile(project(":"))
        compileOnly(files("${rootProject.projectDir}/deps/$clionFullName/lib/clion.jar"))
        testCompile(project(":", "testOutput"))
    }
}

project(":toml") {
    intellij {
        setPlugins(project(":intellij-toml"))
    }
    dependencies {
        compile(project(":"))
        testCompile(project(":", "testOutput"))
    }
}

project(":intelliLang") {
    intellij {
        setPlugins("IntelliLang")
    }
    dependencies {
        compile(project(":"))
        testCompile(project(":", "testOutput"))
    }
}

project(":copyright") {
    intellij {
        setPlugins("copyright")
    }
    dependencies {
        compile(project(":"))
        testCompile(project(":", "testOutput"))
    }
}

project(":intellij-toml") {
    version = "0.2.0.${prop("buildNumber")}$channelSuffix"

    val generateTomlLexer = task<GenerateLexer>("generateTomlLexer") {
        source = "src/main/grammars/TomlLexer.flex"
        targetDir = "src/gen/org/toml/lang/parse"
        targetClass = "_TomlLexer"
        purgeOldFiles = true
    }

    val generateTomlParser = task<GenerateParser>("generateTomlParser") {
        source = "src/main/grammars/TomlParser.bnf"
        targetRoot = "src/gen"
        pathToParser = "/org/toml/lang/parse/TomlParser.java"
        pathToPsiRoot = "/org/toml/lang/psi"
        purgeOldFiles = true
    }

    tasks.withType<KotlinCompile> {
        dependsOn(generateTomlLexer, generateTomlParser)
    }
}

task("makeRelease") {
    doLast {
        val newChangelog = commitChangelog()
        val newChangelogPath = newChangelog
            .replace(".markdown", "")
            .replaceFirst("-", "/").replaceFirst("-", "/").replaceFirst("-", "/")
        val pluginXmlPath = "./src/main/resources/META-INF/plugin.xml"
        val pluginXml = File(pluginXmlPath)
        val oldText = pluginXml.readText()
        val newText = oldText.replace(
            """https://intellij-rust\.github\.io/(.*)\.html""".toRegex(),
            "https://intellij-rust.github.io/$newChangelogPath.html"
        )
        pluginXml.writeText(newText)
        "git add $pluginXmlPath".execute()
        "git commit -m Changelog".execute()
        "git push".execute()
        commitNightly()
    }
}

fun commitChangelog(): String {
    val website = "../intellij-rust.github.io"
    val lastPost = File("$website/_posts").listFiles()
        .map { it.name }
        .sorted()
        .last()
    val postNumber = lastPost.substringAfterLast("-").substringBefore(".").toInt()
    "python3 changelog.py -c".execute(website)
    "git add _posts/$lastPost".execute(website)
    listOf("git", "commit", "-m", "Changelog $postNumber").execute(website)
    println()
    "git show HEAD".execute(website)
    println("Does ^^ look right? Answer `yes` to push changes\n")
    val yes = readLine()!!.trim() == "yes"
    if (!yes) error("Human says no")
    "git push".execute(website)
    return lastPost
}

fun commitNightly() {
    // TODO: extract the latest versions of all supported platforms
    val ideaArtifactName = "$platformVersion-EAP-SNAPSHOT"

    val versionUrl = URL("https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/BUILD/$ideaArtifactName/BUILD-$ideaArtifactName.txt")
    val ideaVersion = versionUrl.openStream().bufferedReader().readLine().trim()
    println("\n    NEW IDEA: $ideaVersion\n")

    "rustup update nightly".execute()
    val version = "rustup run nightly rustc --version".execute()
    val date = """\d\d\d\d-\d\d-\d\d""".toRegex().find(version)!!.value
    val rustVersion = "nightly-$date"
    println("\n    NEW RUST: $rustVersion\n")

    val travisYml = File(rootProject.projectDir, ".travis.yml")
    val updated = travisYml.readLines().joinToString("\n") { line ->
        if ("modified by script" in line) {
            line.replace("""RUST_VERSION=[\w\-\.]+""".toRegex(), "RUST_VERSION=$rustVersion")
                .replace("""ORG_GRADLE_PROJECT_ideaVersion=[\w\-\.]+""".toRegex(), "ORG_GRADLE_PROJECT_ideaVersion=$ideaVersion")
        } else {
            line
        }
    }
    travisYml.writeText(updated)
    "git branch -Df nightly".execute(ignoreExitCode = true)
    "git checkout -b nightly".execute()
    "git add .travis.yml".execute()
    listOf("git", "commit", "-m", ":arrow_up: nightly IDEA & rust").execute()
    "git push origin nightly".execute()
}

task("updateCompilerFeatures") {
    doLast {
        val featureGateUrl = URL("https://raw.githubusercontent.com/rust-lang/rust/master/src/libsyntax/feature_gate.rs")
        val text = featureGateUrl.openStream().bufferedReader().readText()
        val file = File("src/main/kotlin/org/rust/lang/core/CompilerFeatures.kt")
        file.bufferedWriter().use {
            it.writeln("""
                /*
                 * Use of this source code is governed by the MIT license that can be
                 * found in the LICENSE file.
                 */

                @file:Suppress("unused")

                package org.rust.lang.core

                import org.rust.lang.core.FeatureState.*

            """.trimIndent())
            it.writeFeatures("active", text)
            it.writeln()
            it.writeFeatures("accepted", text)
        }
    }
}

fun Writer.writeFeatures(featureSet: String, text: String) {
    """((\s*//.*\n)*)\s*\($featureSet, (\w+), (\"\d+\.\d+\.\d+\"), .*\),"""
        .toRegex(RegexOption.MULTILINE)
        .findAll(text)
        .forEach { matcher ->
            val (comments, _, featureName, version) = matcher.destructured
            if (comments.isNotEmpty()) {
                writeln(comments.trimIndent().trim())
            }
            writeln("""val ${featureName.toUpperCase()} = CompilerFeature("$featureName", ${featureSet.toUpperCase()}, $version)""")
        }
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

val SourceSet.kotlin: SourceDirectorySet
    get() =
        (this as HasConvention)
            .convention
            .getPlugin(KotlinSourceSet::class.java)
            .kotlin


fun SourceSet.kotlin(action: SourceDirectorySet.() -> Unit) =
    kotlin.action()


fun String.execute(wd: String? = null, ignoreExitCode: Boolean = false): String =
    split(" ").execute(wd, ignoreExitCode)

fun List<String>.execute(wd: String? = null, ignoreExitCode: Boolean = false): String {
    val process = ProcessBuilder(this)
        .also { pb -> wd?.let { pb.directory(File(it)) } }
        .start()
    var result = ""
    val errReader = thread { process.errorStream.bufferedReader().forEachLine { println(it) } }
    val outReader = thread {
        process.inputStream.bufferedReader().forEachLine { line ->
            println(line)
            result += line
        }
    }
    process.waitFor()
    outReader.join()
    errReader.join()
    if (process.exitValue() != 0 && !ignoreExitCode) error("Non-zero exit status for `$this`")
    return result
}

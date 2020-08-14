import org.apache.tools.ant.taskdefs.condition.Os.*
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.Writer
import java.net.URL
import kotlin.concurrent.thread

// The same as `--full-stacktrace` param
gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL

val CI = System.getenv("CI") != null
val TEAMCITY = System.getenv("TEAMCITY_VERSION") != null

val channel = prop("publishChannel")
val platformVersion = prop("platformVersion").toInt()
val baseIDE = prop("baseIDE")
val ideaVersion = prop("ideaVersion")
val clionVersion = prop("clionVersion")
val baseVersion = when (baseIDE) {
    "idea" -> ideaVersion
    "clion" -> clionVersion
    else -> error("Unexpected IDE name: `$baseIDE`")
}

val nativeDebugPlugin = "com.intellij.nativeDebug:${prop("nativeDebugPluginVersion")}"
val graziePlugin = "tanvd.grazi:${prop("graziePluginVersion")}"
val psiViewerPlugin = "PsiViewer:${prop("psiViewerPluginVersion")}"

plugins {
    idea
    kotlin("jvm") version "1.3.72"
    id("org.jetbrains.intellij") version "0.4.21"
    id("org.jetbrains.grammarkit") version "2020.2.1"
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
        jcenter()
        maven("https://dl.bintray.com/jetbrains/markdown")
    }

    idea {
        module {
            generatedSourceDirs.add(file("src/gen"))
        }
    }

    intellij {
        version = baseVersion
        downloadSources = !CI
        updateSinceUntilBuild = true
        instrumentCode = false
        ideaDependencyCachePath = dependencyCachePath
        sandboxDirectory = "$buildDir/$baseIDE-sandbox-$platformVersion"
    }

    tasks {
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                languageVersion = "1.3"
                apiVersion = "1.3"
                freeCompilerArgs = listOf("-Xjvm-default=enable")
            }
        }
        withType<PatchPluginXmlTask> {
            sinceBuild(prop("sinceBuild"))
            untilBuild(prop("untilBuild"))
        }

        buildSearchableOptions {
            // buildSearchableOptions task doesn't make sense for non-root subprojects
            val isRootProject = project.name in listOf("plugin", "intellij-toml")
            enabled = isRootProject && prop("enableBuildSearchableOptions").toBoolean()
        }

        test {
            testLogging.showStandardStreams = prop("showStandardStreams").toBoolean()
        }
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = VERSION_1_8
        targetCompatibility = VERSION_1_8
    }

    sourceSets {
        main {
            java.srcDirs("src/gen")
            kotlin.srcDirs("src/$platformVersion/main/kotlin")
            resources.srcDirs("src/$platformVersion/main/resources")
        }
        test {
            kotlin.srcDirs("src/$platformVersion/test/kotlin")
            resources.srcDirs("src/$platformVersion/test/resources")
        }
    }

    afterEvaluate {
        tasks.withType<AbstractTestTask> {
            testLogging {
                if (hasProp("showTestStatus") && prop("showTestStatus").toBoolean()) {
                    events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                    exceptionFormat = TestExceptionFormat.FULL
                }
            }
        }

        tasks.withType<Test>().configureEach {
            jvmArgs = listOf("-Xmx2g", "-XX:-OmitStackTraceInFastThrow")
            // We need to prevent the platform-specific shared JNA library to loading from the system library paths,
            // because otherwise it can lead to compatibility issues.
            // Also note that IDEA does the same thing at startup, and not only for tests.
            systemProperty("jna.nosys", "true")
            if (TEAMCITY) {
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


val Project.dependencyCachePath get(): String {
    val cachePath = file("${rootProject.projectDir}/deps")
    // If cache path doesn't exist, we need to create it manually
    // because otherwise gradle-intellij-plugin will ignore it
    if (!cachePath.exists()) {
        cachePath.mkdirs()
    }
    return cachePath.absolutePath
}

val channelSuffix = if (channel.isBlank() || channel == "stable") "" else "-$channel"
val versionSuffix = "-$platformVersion$channelSuffix"
val majorVersion = "0.3"
val patchVersion = prop("patchVersion").toInt()

// Special module with run, build and publish tasks
project(":plugin") {
    version = "$majorVersion.$patchVersion.${prop("buildNumber")}$versionSuffix"
    intellij {
        pluginName = "intellij-rust"
        val plugins = mutableListOf(
            project(":intellij-toml"),
            "IntelliLang",
            graziePlugin,
            psiViewerPlugin
        )
        if (baseIDE == "idea") {
            plugins += listOf(
                "copyright",
                "java",
                nativeDebugPlugin
            )
        }
        setPlugins(*plugins.toTypedArray())
    }

    dependencies {
        implementation(project(":"))
        implementation(project(":idea"))
        implementation(project(":clion"))
        implementation(project(":debugger"))
        implementation(project(":toml"))
        implementation(project(":copyright"))
        implementation(project(":coverage"))
        implementation(project(":intelliLang"))
        implementation(project(":duplicates"))
        implementation(project(":grazie"))
    }

    tasks {
        buildPlugin {
            // Set proper name for final plugin zip.
            // Otherwise, base name is the same as gradle module name
            archiveBaseName.set("intellij-rust")
        }

        withType<PrepareSandboxTask> {
            from("$rootDir/prettyPrinters") {
                into("${intellij.pluginName}/prettyPrinters")
                include("*.py")
            }
        }

        withType<RunIdeTask> {
            // Default args for IDEA installation
            jvmArgs("-Xmx768m", "-XX:+UseConcMarkSweepGC", "-XX:SoftRefLRUPolicyMSPerMB=50")
            // Disable auto plugin resloading. See `com.intellij.ide.plugins.DynamicPluginVfsListener`
            jvmArgs("-Didea.auto.reload.plugins=false")
            // uncomment if `unexpected exception ProcessCanceledException` prevents you from debugging a running IDE
            // jvmArgs("-Didea.ProcessCanceledException=disabled")
        }

        withType<PatchPluginXmlTask> {
            pluginDescription(file("description.html").readText())
        }

        withType<PublishTask> {
            token(prop("publishToken"))
            channels(channel)
        }
    }
}

project(":") {
    sourceSets {
        main {
            if (channel == "nightly" || channel == "dev") {
                resources.srcDirs("src/main/resources-nightly")
            } else {
                resources.srcDirs("src/main/resources-stable")
            }
        }
    }

    val testOutput = configurations.create("testOutput")

    dependencies {
        implementation(project(":common"))
        implementation("org.jetbrains:markdown:0.1.30") {
            exclude(module = "kotlin-runtime")
            exclude(module = "kotlin-stdlib")
        }
        testImplementation(project(":common", "testOutput"))
        testOutput(sourceSets.getByName("test").output.classesDirs)
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

    tasks.withType<KotlinCompile> {
        dependsOn(
            generateRustLexer, generateRustDocHighlightingLexer,
            generateRustParser
        )
    }

    tasks.withType<Test> {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    task("resolveDependencies") {
        doLast {
            rootProject.allprojects
                .map { it.configurations }
                .flatMap { listOf(it.compile, it.testCompile) }
                .forEach { it.get().resolve() }
        }
    }
}

project(":idea") {
    intellij {
        version = ideaVersion
        setPlugins("java")
    }
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":clion") {
    intellij {
        version = clionVersion
    }
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        implementation(project(":debugger"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":debugger") {
    intellij {
        if (baseIDE == "idea") {
            setPlugins(nativeDebugPlugin)
        } else {
            version = clionVersion
        }
    }
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":toml") {
    intellij {
        setPlugins(project(":intellij-toml"))
    }
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":intelliLang") {
    intellij {
        setPlugins("IntelliLang")
    }
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":copyright") {
    intellij {
        version = ideaVersion
        setPlugins("copyright")
    }
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":duplicates") {
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":coverage") {
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":grazie") {
    intellij {
        setPlugins(graziePlugin)
    }
    dependencies {
        implementation(project(":"))
        implementation(project(":common"))
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":intellij-toml") {
    version = "0.2.$patchVersion.${prop("buildNumber")}$versionSuffix"

    dependencies {
        implementation(project(":common"))
        testImplementation(project(":common", "testOutput"))
    }

    val generateTomlLexer = task<GenerateLexer>("generateTomlLexer") {
        source = "src/main/grammars/TomlLexer.flex"
        targetDir = "src/gen/org/toml/lang/lexer"
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

    tasks {
        withType<KotlinCompile> {
            dependsOn(generateTomlLexer, generateTomlParser)
        }
        withType<PublishTask> {
            token(prop("publishToken"))
            channels(channel)
        }
    }
}

project(":common") {
    val testOutput = configurations.create("testOutput")

    dependencies {
        testOutput(sourceSets.getByName("test").output.classesDirs)
    }
}

task("runPrettyPrintersTests") {
    doLast {
        val lldbPath = when {
            // TODO: Use `lldb` Python module from CLion distribution
            isFamily(FAMILY_MAC) -> "/Applications/Xcode.app/Contents/SharedFrameworks/LLDB.framework/Resources/Python"
            isFamily(FAMILY_UNIX) -> "$projectDir/deps/${clionVersion.replaceFirst("CL", "clion")}/bin/lldb/linux/lib/python3.6/site-packages"
            else -> error("Unsupported OS")
        }
        "cargo run --package pretty_printers_test --bin pretty_printers_test -- lldb $lldbPath".execute("pretty_printers_tests")

        val gdbBinary = when {
            isFamily(FAMILY_MAC) -> "$projectDir/deps/${clionVersion.replaceFirst("CL", "clion")}/bin/gdb/mac/bin/gdb"
            isFamily(FAMILY_UNIX) -> "$projectDir/deps/${clionVersion.replaceFirst("CL", "clion")}/bin/gdb/linux/bin/gdb"
            else -> error("Unsupported OS")
        }
        "cargo run --package pretty_printers_test --bin pretty_printers_test -- gdb $gdbBinary".execute("pretty_printers_tests")
    }
}

task("updateCompilerFeatures") {
    doLast {
        val file = File("src/main/kotlin/org/rust/lang/core/CompilerFeatures.kt")
        file.bufferedWriter().use {
            it.writeln("""
                /*
                 * Use of this source code is governed by the MIT license that can be
                 * found in the LICENSE file.
                 */

                @file:Suppress("unused")

                package org.rust.lang.core

                import org.rust.lang.core.FeatureState.ACCEPTED
                import org.rust.lang.core.FeatureState.ACTIVE

            """.trimIndent())
            it.writeFeatures("active", "https://raw.githubusercontent.com/rust-lang/rust/master/src/librustc_feature/active.rs")
            it.writeln()
            it.writeFeatures("accepted", "https://raw.githubusercontent.com/rust-lang/rust/master/src/librustc_feature/accepted.rs")
        }
    }
}

fun Writer.writeFeatures(featureSet: String, remoteFileUrl: String) {
    val text = URL(remoteFileUrl).openStream().bufferedReader().readText()
    val commentRegex = "^/{2,}".toRegex()
    """((\s*//.*\n)*)\s*\($featureSet, (\w+), (\"\d+\.\d+\.\d+\"), .*\),"""
        .toRegex(RegexOption.MULTILINE)
        .findAll(text)
        .forEach { matcher ->
            val (comments, _, featureName, version) = matcher.destructured
            if (comments.isNotEmpty()) {
                comments.trimIndent().trim().lines().forEach { line ->
                    writeln(line.replace(commentRegex, "//"))
                }
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

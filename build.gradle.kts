import groovy.json.JsonSlurper
import org.apache.tools.ant.taskdefs.condition.Os.*
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jsoup.Jsoup
import java.io.Writer
import java.net.URL
import kotlin.concurrent.thread

// The same as `--stacktrace` param
gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS

val isCI = System.getenv("CI") != null
val isTeamcity = System.getenv("TEAMCITY_VERSION") != null

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
// BACKCOMAPT: 2021.1
val graziePlugin = if (platformVersion >= 212 || baseIDE == "idea") {
    "tanvd.grazi"
} else {
    "tanvd.grazi:${prop("graziePluginVersion")}"
}
val psiViewerPlugin = "PsiViewer:${prop("psiViewerPluginVersion")}"
val intelliLangPlugin = "org.intellij.intelliLang"
val copyrightPlugin = "com.intellij.copyright"
val javaPlugin = "com.intellij.java"
val javaIdePlugin = "com.intellij.java.ide"
val javaScriptPlugin = "JavaScript"
val clionPlugins = listOf("com.intellij.cidr.base", "com.intellij.clion")
val mlCompletionPlugin = "com.intellij.completion.ml.ranking"

val compileNativeCodeTaskName = "compileNativeCode"

plugins {
    idea
    kotlin("jvm") version "1.4.32"
    id("org.jetbrains.intellij") version "0.7.2"
    id("org.jetbrains.grammarkit") version "2021.1.3"
    id("net.saliman.properties") version "1.5.1"
    id("org.gradle.test-retry") version "1.2.0"
}

idea {
    module {
        // https://github.com/gradle/kotlin-dsl/issues/537/
        excludeDirs = excludeDirs + file("testData") + file("deps") + file("bin") + file("build-cache")
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

    repositories {
        mavenCentral()
        jcenter()
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }

    configurations {
        all {
            // Allows using project dependencies instead of IDE dependencies during compilation and test running
            resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.DEPENDENCY_FIRST)
        }
    }

    idea {
        module {
            generatedSourceDirs.add(file("src/gen"))
        }
    }

    intellij {
        version = baseVersion
        downloadSources = !isCI
        updateSinceUntilBuild = true
        instrumentCode = false
        ideaDependencyCachePath = dependencyCachePath
        sandboxDirectory = "$buildDir/$baseIDE-sandbox-$platformVersion"
    }

    tasks {
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                languageVersion = "1.4"
                apiVersion = "1.4"
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
            // TODO: enable buildSearchableOptions task for 212 platform
            enabled = isRootProject && prop("enableBuildSearchableOptions").toBoolean() && platformVersion < 212
        }

        test {
            testLogging.showStandardStreams = prop("showStandardStreams").toBoolean()
            if (isCI) {
                retry {
                    maxRetries.set(3)
                    maxFailures.set(5)
                }
            }
        }

        // It makes sense to copy native binaries only for root ("intellij-rust") and "plugin" projects because:
        // - `intellij-rust` is supposed to provide all necessary functionality related to procedural macro expander.
        //   So the binaries are required for the corresponding tests.
        // - `plugin` is root project to build plugin artifact and exactly its sandbox is included into the plugin artifact
        if (project.name in listOf("intellij-rust", "plugin")) {
            task<Exec>(compileNativeCodeTaskName) {
                workingDir = rootDir.resolve("native-helper")
                executable = "cargo"
                // Hack to use unstable `--out-dir` option work for stable toolchain
                // https://doc.rust-lang.org/cargo/commands/cargo-build.html#output-options
                environment("RUSTC_BOOTSTRAP", "1")

                val hostPlatform = DefaultNativePlatform.host()
                val outDir = "${rootDir}/bin/${hostPlatform.operatingSystem.toFamilyName()}/${hostPlatform.architecture.name}"
                args("build", "--release", "-Z", "unstable-options", "--out-dir", outDir)

                // It may be useful to disable compilation of native code.
                // For example, CI builds native code for each platform in separate tasks and puts it into `bin` dir manually
                // so there is no need to do it again.
                enabled = prop("compileNativeCode").toBoolean()
            }
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

    dependencies {
        compileOnly(kotlin("stdlib-jdk8"))
    }

    afterEvaluate {
        tasks.withType<AbstractTestTask> {
            testLogging {
                if (hasProp("showTestStatus") && prop("showTestStatus").toBoolean()) {
                    events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
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
            if (isTeamcity) {
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

val channelSuffix = if (channel.isBlank() || channel == "stable") "" else "-$channel"
val versionSuffix = "-$platformVersion$channelSuffix"
val majorVersion = "0.4"
val patchVersion = prop("patchVersion").toInt()

// Special module with run, build and publish tasks
project(":plugin") {
    version = "$majorVersion.$patchVersion.${prop("buildNumber")}$versionSuffix"
    intellij {
        pluginName = "intellij-rust"
        val plugins = mutableListOf(
            project(":intellij-toml"),
            intelliLangPlugin,
            graziePlugin,
            psiViewerPlugin,
            mlCompletionPlugin
        )
        if (platformVersion < 212) {
            plugins += javaScriptPlugin
        }
        if (baseIDE == "idea") {
            plugins += listOf(
                copyrightPlugin,
                javaPlugin,
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
        if (platformVersion < 212) {
            implementation(project(":js"))
        }
        implementation(project(":ml-completion"))
    }

    tasks {
        buildPlugin {
            // Set proper name for final plugin zip.
            // Otherwise, base name is the same as gradle module name
            archiveBaseName.set("intellij-rust")
        }

        withType<PrepareSandboxTask> {
            dependsOn(named(compileNativeCodeTaskName))

            // Copy native binaries
            from("${rootDir}/bin") {
                into("intellij-rust/bin")
                include("**")
            }
            // Copy pretty printers
            from("$rootDir/prettyPrinters") {
                into("${intellij.pluginName}/prettyPrinters")
                include("*.py")
            }
        }

        withType<RunIdeTask> {
            // Default args for IDEA installation
            jvmArgs("-Xmx768m", "-XX:+UseConcMarkSweepGC", "-XX:SoftRefLRUPolicyMSPerMB=50")
            // Disable plugin auto reloading. See `com.intellij.ide.plugins.DynamicPluginVfsListener`
            jvmArgs("-Didea.auto.reload.plugins=false")
            // Don't show "Tip of the Day" at startup
            jvmArgs("-Dide.show.tips.on.startup.default.value=false")
            // uncomment if `unexpected exception ProcessCanceledException` prevents you from debugging a running IDE
            // jvmArgs("-Didea.ProcessCanceledException=disabled")

            // Uncomment to enable FUS testing mode
            // jvmArgs("-Dfus.internal.test.mode=true")
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
        implementation("org.jetbrains:markdown:0.2.0") {
            exclude(module = "kotlin-runtime")
            exclude(module = "kotlin-stdlib")
            exclude(module = "kotlin-stdlib-common")
        }
        testImplementation(project(":common", "testOutput"))
        testImplementation("com.squareup.okhttp3:mockwebserver:4.9.0")
        testOutput(sourceSets.getByName("test").output.classesDirs)
    }

    val generateRustLexer = task<GenerateLexer>("generateRustLexer") {
        source = "src/main/grammars/RustLexer.flex"
        targetDir = "src/gen/org/rust/lang/core/lexer"
        targetClass = "_RustLexer"
        purgeOldFiles = true
    }

    val deleteOldRustDocHighlightingLexer = task<Delete>("deleteOldRustDocHighlightingLexer") {
        delete("src/gen/org/rust/lang/doc")
    }

    val generateRustParser = task<GenerateParser>("generateRustParser") {
        source = "src/main/grammars/RustParser.bnf"
        targetRoot = "src/gen"
        pathToParser = "/org/rust/lang/core/parser/RustParser.java"
        pathToPsiRoot = "/org/rust/lang/core/psi"
        purgeOldFiles = true
    }

    tasks {
        withType<KotlinCompile> {
            dependsOn(
                generateRustLexer, deleteOldRustDocHighlightingLexer,
                generateRustParser
            )
        }

        // In tests `resources` directory is used instead of `sandbox`
        processTestResources {
            dependsOn(named(compileNativeCodeTaskName))
            from("${rootDir}/bin") {
                into("bin")
                include("**")
            }
        }

        clean {
            delete(*(File("${rootDir}/build-cache").listFiles() ?: emptyArray()))
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
        setPlugins(
            javaPlugin,
            // this plugin registers `com.intellij.ide.projectView.impl.ProjectViewPane` for IDEA that we use in tests
            javaIdePlugin
        )
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
        setPlugins(*clionPlugins.toTypedArray())
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
            setPlugins(*clionPlugins.toTypedArray())
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
        implementation("org.eclipse.jgit:org.eclipse.jgit:5.9.0.202009080501-r") { exclude("org.slf4j") }
        implementation("com.vdurmont:semver4j:3.1.0")
        testImplementation(project(":", "testOutput"))
        testImplementation(project(":common", "testOutput"))
    }
}

project(":intelliLang") {
    intellij {
        setPlugins(intelliLangPlugin)
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
        setPlugins(copyrightPlugin)
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

if (platformVersion < 212) {
    project(":js") {
        intellij {
            setPlugins(javaScriptPlugin)
        }
        dependencies {
            implementation(project(":"))
            implementation(project(":common"))
            testImplementation(project(":", "testOutput"))
            testImplementation(project(":common", "testOutput"))
        }
    }
}

project(":ml-completion") {
    intellij {
        setPlugins(mlCompletionPlugin)
    }
    dependencies {
        implementation("org.jetbrains.intellij.deps.completion:completion-ranking-rust:0.2.2")
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
            isFamily(FAMILY_UNIX) -> "$projectDir/deps/${clionVersion.replaceFirst("CL", "clion")}/bin/lldb/linux/lib/python3.8/site-packages"
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
            it.writeFeatures("active", "https://raw.githubusercontent.com/rust-lang/rust/master/compiler/rustc_feature/src/active.rs")
            it.writeln()
            it.writeFeatures("accepted", "https://raw.githubusercontent.com/rust-lang/rust/master/compiler/rustc_feature/src/accepted.rs")
        }
    }
}

task("updateCargoOptions") {
    doLast {
        val file = File("src/main/kotlin/org/rust/cargo/util/CargoOptions.kt")
        file.bufferedWriter().use {
            it.writeln("""
                /*
                 * Use of this source code is governed by the MIT license that can be
                 * found in the LICENSE file.
                 */

                package org.rust.cargo.util

                data class CargoOption(val name: String, val description: String) {
                    val longName: String get() = "--${'$'}name"
                }

            """.trimIndent())
            it.writeCargoOptions("https://doc.rust-lang.org/cargo/commands")
        }
    }
}

task("updateLints") {
    doLast {
        val lints = JsonSlurper().parseText("python3 fetch_lints.py".execute("scripts", print = false)) as List<Map<String, *>>

        fun Map<String, *>.isGroup(): Boolean = get("group") as Boolean
        fun Map<String, *>.isRustcLint(): Boolean = get("rustc") as Boolean
        fun Map<String, *>.getName(): String = get("name") as String

        fun writeLints(path: String, lints: List<Map<String, *>>, variableName: String) {
            val file = File(path)
            val items = lints.sortedWith(compareBy({ !it.isGroup() }, { it.getName() })).joinToString(
                separator = ",\n    "
            ) {
                val name = it.getName()
                val isGroup = it.isGroup()
                "Lint(\"$name\", $isGroup)"
            }
            file.bufferedWriter().use {
                it.writeln("""
/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion.lint

val $variableName: List<Lint> = listOf(
    $items
)
""".trim())
            }
        }

        writeLints(
            "src/main/kotlin/org/rust/lang/core/completion/lint/RustcLints.kt",
            lints.filter { it.isRustcLint() },
            "RUSTC_LINTS"
        )
        writeLints(
            "src/main/kotlin/org/rust/lang/core/completion/lint/ClippyLints.kt",
            lints.filter { !it.isRustcLint() },
            "CLIPPY_LINTS"
        )
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

        writeln("""
        |    $variantName(
        |        description = "${command.description}",
        |        options = ${if (command.options.isEmpty()) "emptyList()" else "listOf($renderedOptions)"}
        |    )${if (isLast) ";" else ","}
        """.trimMargin())
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

val SourceSet.kotlin: SourceDirectorySet
    get() =
        (this as HasConvention)
            .convention
            .getPlugin(KotlinSourceSet::class.java)
            .kotlin


fun SourceSet.kotlin(action: SourceDirectorySet.() -> Unit) =
    kotlin.action()


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

import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.SourceSet
import org.jetbrains.grammarkit.GrammarKitPluginExtension
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.tasks.Jar
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import kotlin.concurrent.thread

buildscript {
    repositories {
        maven { setUrl("https://jitpack.io") }
    }
    dependencies {
        classpath("com.github.hurricup:gradle-grammar-kit-plugin:2017.1.1")
    }
}


val CI = System.getenv("CI") != null

val channel = prop("publishChannel")

plugins {
    idea
    kotlin("jvm") version "1.2.10"
    id("org.jetbrains.intellij") version "0.2.17"
    id("de.undercouch.download") version "3.2.0"
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
    }

    idea {
        module {
            generatedSourceDirs.add(file("src/gen"))
        }
    }

    intellij {
        version = prop("ideaVersion")
        downloadSources = !CI
        updateSinceUntilBuild = false
        instrumentCode = false
        ideaDependencyCachePath = file("deps").absolutePath
    }

    configure<GrammarKitPluginExtension> {
        grammarKitRelease = "1.5.2"
    }

    tasks.withType<PublishTask> {
        username(prop("publishUsername"))
        password(prop("publishPassword"))
        channels(channel)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            languageVersion = "1.1"
            apiVersion = "1.1"
        }
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = VERSION_1_8
        targetCompatibility = VERSION_1_8
    }

    java.sourceSets {
        getByName("main").java.srcDirs("src/gen")
    }
}

val versionSuffix = if (channel.isBlank()) "" else "-$channel"

project(":") {
    val clionVersion = prop("clionVersion")
    version = "0.2.0.${prop("buildNumber")}$versionSuffix"
    intellij {
        pluginName = "intellij-rust"
//        alternativeIdePath = "deps/clion-$clionVersion"
        setPlugins("org.toml.lang:0.2.0.9")
    }

    repositories {
        maven { setUrl("https://dl.bintray.com/jetbrains/markdown") }
    }

    dependencies {
        compile("org.jetbrains:markdown:0.1.12") {
            exclude(module = "kotlin-runtime")
            exclude(module = "kotlin-stdlib")
        }
    }

    java.sourceSets {
        create("debugger") {
            kotlin.srcDirs("debugger/src/main/kotlin")
            compileClasspath += getByName("main").compileClasspath +
                getByName("main").output +
                files("deps/clion-$clionVersion/lib/clion.jar")
        }
    }

    tasks.withType<Jar> {
        from(java.sourceSets.getByName("debugger").output)
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
        onlyIf { !file("${project.projectDir}/deps/clion-$clionVersion").exists() }
        from(tarTree("deps/clion-$clionVersion.tar.gz"))
        into(file("${project.projectDir}/deps"))
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
}

project(":intellij-toml") {
    version = "0.2.0.${prop("buildNumber")}$versionSuffix"

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
        val pluginXml = File("./src/main/resources/META-INF/plugin.xml")
        val oldText = pluginXml.readText()
        val newText = oldText.replace(
            """https://intellij-rust\.github\.io/(.*)\.html""".toRegex(),
            "https://intellij-rust.github.io/$newChangelogPath.html"
        )
        pluginXml.writeText(newText)
        "git add .".execute()
        "git commit -am Changelog".execute()
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
    "git add .".execute(website)
    listOf("git", "commit", "-am", "Changelog $postNumber").execute(website)
    println()
    "git show HEAD".execute(website)
    println("Does ^^ look right? Answer `yes` to push changes\n")
    val yes = readLine()!!.trim() == "yes"
    if (!yes) error("Human says no")
    "git push".execute(website)
    return lastPost
}

fun commitNightly() {
    val versionUrl = URL("https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/BUILD/LATEST-EAP-SNAPSHOT/BUILD-LATEST-EAP-SNAPSHOT.txt")
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
            "  - RUST_VERSION=$rustVersion ORG_GRADLE_PROJECT_ideaVersion=$ideaVersion # modified by script"
        } else {
            line
        }
    }
    travisYml.writeText(updated)
    "git branch -Df nightly".execute(ignoreExitCode = true)
    "git checkout -b nightly".execute()
    "git add .travis.yml".execute()
    listOf("git", "commit", "-am", ":arrow_up: nightly IDEA & rust").execute()
    "git push".execute()
}

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

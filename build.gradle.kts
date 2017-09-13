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
    kotlin("jvm") version "1.1.4"
    id("org.jetbrains.intellij") version "0.2.15"
    id("de.undercouch.download") version "3.2.0"
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
    version = "0.1.0.${prop("buildNumber")}$versionSuffix"
    intellij { pluginName = "intellij-rust" }

    idea {
        module {
            generatedSourceDirs.add(file("tstData"))
        }
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
        getByName("main").kotlin.srcDirs("debugger/src/main/kotlin")
        getByName("main").compileClasspath += files("debugger/lib/clion-$clionVersion/lib/clion.jar")
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
        src("https://download.jetbrains.com/cpp/CLion-$clionVersion.tar.gz")
        dest(file("${project.projectDir}/debugger/lib/clion-$clionVersion.tar.gz"))
        overwrite(false)
    }
    val unpackClion = task<Copy>("unpackClion") {
        from(tarTree("debugger/lib/clion-$clionVersion.tar.gz"))
        into(file("${project.projectDir}/debugger/lib"))
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
}

project(":toml") {
    version = "0.1.0.${prop("buildNumber")}$versionSuffix"
    intellij { pluginName = "intellij-toml" }

    val generateTomlLexer = task<GenerateLexer>("generateTomlLexer") {
        source = "src/main/grammars/TomlLexer.flex"
        targetDir = "src/gen/org/toml/lang/core/lexer"
        targetClass = "_TomlLexer"
        purgeOldFiles = true
    }

    val generateTomlParser = task<GenerateParser>("generateTomlParser") {
        source = "src/main/grammars/TomlParser.bnf"
        targetRoot = "src/gen"
        pathToParser = "/org/toml/lang/core/parser/TomlParser.java"
        pathToPsiRoot = "/org/toml/lang/core/psi"
        purgeOldFiles = true
    }

    tasks.withType<KotlinCompile> {
        dependsOn(generateTomlLexer, generateTomlParser)
    }
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

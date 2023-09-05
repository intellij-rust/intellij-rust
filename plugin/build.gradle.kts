import groovy.xml.XmlParser
import ijrust.utils.not
import java.io.Writer
import kotlin.concurrent.thread
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


plugins {
    ijrust.conventions.intellij
}

version = ijRustBuild.version
description = "Special module with run, build and publish tasks"

intellij {
    pluginName.set("intellij-rust")
    val pluginList = mutableListOf(
        ijRustBuild.tomlPlugin,
        ijRustBuild.intelliLangPlugin,
        ijRustBuild.graziePlugin,
        ijRustBuild.psiViewerPlugin,
        ijRustBuild.javaScriptPlugin,
        ijRustBuild.mlCompletionPlugin
    )
//    if (ijRustBuild.baseIDE.get() == "idea") {
//        pluginList += listOf(
//            ijRustBuild.copyrightPlugin,
//            ijRustBuild.javaPlugin,
//            ijRustBuild.nativeDebugPlugin
//        )
//    }
    plugins.set(pluginList)
}

dependencies {
    implementation(project(":"))
    implementation(project(":idea"))
    implementation(project(":clion"))
    implementation(project(":debugger"))
    implementation(project(":profiler"))
    implementation(project(":copyright"))
    implementation(project(":coverage"))
    implementation(project(":intelliLang"))
    implementation(project(":duplicates"))
    implementation(project(":grazie"))
    implementation(project(":js"))
    implementation(project(":ml-completion"))
}

// Collects all jars produced by compilation of project modules and merges them into singe one.
// We need to put all plugin manifest files into single jar to make new plugin model work
val mergePluginJarTask = task<Jar>("mergePluginJars") {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    archiveBaseName.set(ijRustBuild.basePluginArchiveName)

    exclude("META-INF/MANIFEST.MF")
    exclude("**/classpath.index")

    val pluginLibDir by lazy {
        val sandboxTask = tasks.prepareSandbox.get()
        sandboxTask.destinationDir.resolve("${sandboxTask.pluginName.get()}/lib")
    }

    val pluginJars by lazy {
        pluginLibDir.listFiles().orEmpty().filter { it.isPluginJar() }
    }

    destinationDirectory.set(project.layout.dir(provider { pluginLibDir }))

    doFirst {
        for (file in pluginJars) {
            from(zipTree(file))
        }
    }

    doLast {
        delete(pluginJars)
    }
}

// Add plugin sources to the plugin ZIP.
// gradle-intellij-plugin will use it as a plugin sources if the plugin is used as a dependency
val createSourceJar = task<Jar>("createSourceJar") {
    dependsOn(":generateLexer")
    dependsOn(":generateParser")
    dependsOn(":debugger:generateGrammarSource")

//    for (prj in pluginProjects) {
//        from(prj.kotlin.sourceSets.main.get().kotlin) {
//            include("**/*.java")
//            include("**/*.kt")
//        }
//    }

    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveBaseName.set(ijRustBuild.basePluginArchiveName)
    archiveClassifier.set("src")
}

tasks {
    buildPlugin {
        dependsOn(createSourceJar)
        from(createSourceJar) { into("lib/src") }
        // Set proper name for final plugin zip.
        // Otherwise, base name is the same as gradle module name
        archiveBaseName.set(ijRustBuild.basePluginArchiveName)
    }
    runIde { enabled = true }
    prepareSandbox {
        finalizedBy(mergePluginJarTask)
        enabled = true
    }
    buildSearchableOptions {
        // Force `mergePluginJarTask` be executed before `buildSearchableOptions`
        // Otherwise, `buildSearchableOptions` task can't load the plugin and searchable options are not built.
        // Should be dropped when jar merging is implemented in `gradle-intellij-plugin` itself
        dependsOn(mergePluginJarTask)
        enabled = ijRustBuild.enableBuildSearchableOptions.get()
    }
    withType<PrepareSandboxTask> {
        dependsOn(named(ijRustBuild.compileNativeCodeTaskName))

        // Copy native binaries
        from("${rootDir}/bin") {
            into("${pluginName.get()}/bin")
            include("**")
        }
        // Copy pretty printers
        from("$rootDir/prettyPrinters") {
            into("${pluginName.get()}/prettyPrinters")
            include("**/*.py")
        }
    }

    withType<RunIdeTask> {
        // Default args for IDEA installation
        jvmArgs("-Xmx768m", "-XX:+UseG1GC", "-XX:SoftRefLRUPolicyMSPerMB=50")
        // Disable plugin auto reloading. See `com.intellij.ide.plugins.DynamicPluginVfsListener`
        jvmArgs("-Didea.auto.reload.plugins=false")
        // Don't show "Tip of the Day" at startup
        jvmArgs("-Dide.show.tips.on.startup.default.value=false")
        // uncomment if `unexpected exception ProcessCanceledException` prevents you from debugging a running IDE
        // jvmArgs("-Didea.ProcessCanceledException=disabled")

        // Uncomment to enable FUS testing mode
        // jvmArgs("-Dfus.internal.test.mode=true")

        // Uncomment to enable localization testing mode
        // jvmArgs("-Didea.l10n=true")
    }

    withType<PatchPluginXmlTask> {
        pluginDescription.set(provider { file("description.html").readText() })
    }

    withType<PublishPluginTask> {
        token.set(ijRustBuild.publishToken)
        channels.set(ijRustBuild.channel.map { listOf(it) })
    }
}

// Generates event scheme for Rust plugin FUS events to `plugin/build/eventScheme.json`
task<RunIdeTask>("buildEventsScheme") {
    dependsOn(tasks.prepareSandbox)
    args("buildEventsScheme", "--outputFile=${buildDir.resolve("eventScheme.json").absolutePath}", "--pluginId=org.rust.lang")
    // BACKCOMPAT: 2023.1. Update value to 232 and this comment
    // `IDEA_BUILD_NUMBER` variable is used by `buildEventsScheme` task to write `buildNumber` to output json.
    // It will be used by TeamCity automation to set minimal IDE version for new events
    environment("IDEA_BUILD_NUMBER", "231")
}

fun File.isPluginJar(): Boolean {
    if (!isFile) return false
    if (extension != "jar") return false
    return zipTree(this).files.any { it.isManifestFile() }
}

fun File.isManifestFile(): Boolean {
    if (extension != "xml") return false
    val rootNode = try {
        val parser = XmlParser()
        parser.parse(this)
    } catch (e: Exception) {
        logger.error("Failed to parse $path", e)
        return false
    }
    return rootNode.name() == "idea-plugin"
}

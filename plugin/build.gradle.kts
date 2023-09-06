import groovy.xml.XmlParser
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.intellij.tasks.RunIdeTask

plugins {
    id("intellij_rust.conventions.intellij")
    id("intellij_rust.conventions.rust-compile")
}

description = "module to build/run/publish Rust plugin"
version = intellijRust.fullVersion.get()


intellij {
    pluginName.set("intellij-rust")

    plugins.addAll(
        intellijRust.tomlPlugin,
        intellijRust.intelliLangPlugin,
        intellijRust.graziePlugin,
        intellijRust.psiViewerPlugin.get(),
        intellijRust.javaScriptPlugin,
        intellijRust.mlCompletionPlugin,
    )
    if (intellijRust.baseIDE.get() == "idea") {
        plugins.addAll(
            intellijRust.copyrightPlugin,
            intellijRust.javaPlugin,
            intellijRust.nativeDebugPlugin.get(),
        )
    }
}

dependencies {
    implementation(projects.intellijRust)
    implementation(projects.idea)
    implementation(projects.clion)
    implementation(projects.debugger)
    implementation(projects.profiler)
    implementation(projects.copyright)
    implementation(projects.coverage)
    implementation(projects.intelliLang)
    implementation(projects.duplicates)
    implementation(projects.grazie)
    implementation(projects.js)
    implementation(projects.mlCompletion)

    kotlinSourcesJar(projects.intellijRust)
    kotlinSourcesJar(projects.idea)
    kotlinSourcesJar(projects.clion)
    kotlinSourcesJar(projects.debugger)
    kotlinSourcesJar(projects.profiler)
    kotlinSourcesJar(projects.copyright)
    kotlinSourcesJar(projects.coverage)
    kotlinSourcesJar(projects.intelliLang)
    kotlinSourcesJar(projects.duplicates)
    kotlinSourcesJar(projects.grazie)
    kotlinSourcesJar(projects.js)
    kotlinSourcesJar(projects.mlCompletion)
}

val mergePluginJar by tasks.registering(Jar::class) {
    group = BasePlugin.BUILD_GROUP

    description = "Collects all jars produced by compilation of project modules and merges them into single one"
    // We need to put all plugin manifest files into single jar to make new plugin model work

    duplicatesStrategy = DuplicatesStrategy.FAIL

    archiveBaseName.set(intellij.pluginName)

    exclude("**/META-INF/MANIFEST.MF")
    exclude("**/classpath.index")

    val archives = serviceOf<ArchiveOperations>()
    val fs = serviceOf<FileSystemOperations>()

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

    fun File.isPluginJar(): Boolean {
        if (!isFile) return false
        if (extension != "jar") return false
        if (name == archiveFileName.get()) return false
        return archives.zipTree(this).files.any { it.isManifestFile() }
    }

    val pluginLibDir: Provider<File> = tasks.prepareSandbox.map { task ->
        task.destinationDir.resolve("${task.pluginName.get()}/lib")
    }

    outputs.dir(pluginLibDir).withPropertyName("pluginLibDir")

    val pluginJars = pluginLibDir.map { dir ->
        dir.listFiles().orEmpty().filter(File::isPluginJar)
    }

    val pluginJarsUnzipped = pluginJars.map { it.map(archives::zipTree) }

    from(pluginJarsUnzipped)

    destinationDirectory.set(objects.directoryProperty().fileProvider(pluginLibDir))

    doLast("remove the merged jars") {
        fs.delete {
            delete(pluginJars)
        }
    }
}

val createSourceJar by tasks.registering(Jar::class) {
    group = BasePlugin.BUILD_GROUP

    description = "Add plugin sources to the plugin ZIP. " +
        "gradle-intellij-plugin will use it as a plugin sources if the plugin is used as a dependency"

    val archives = serviceOf<ArchiveOperations>()

    val kotlinSources = configurations.kotlinSourcesJar.map {
        it.incoming.files.map(archives::zipTree)
    }

    from(kotlinSources) {
        include("*.java")
        include("*.kt")
    }

    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveBaseName.set(intellij.pluginName)
    archiveClassifier.set("src")
}

tasks {
    buildPlugin {
        dependsOn(createSourceJar)
        from(createSourceJar) { into("lib/src") }
        // Set proper name for final plugin zip.
        // Otherwise, base name is the same as gradle module name
        archiveBaseName.set(intellij.pluginName)
    }

    runIde { enabled = true }

    prepareSandbox {
        finalizedBy(mergePluginJar)
        enabled = true
    }

    buildSearchableOptions {
        // Force `mergePluginJar` be executed before `buildSearchableOptions`
        // Otherwise, `buildSearchableOptions` task can't load the plugin and searchable options are not built.
        // Should be dropped when jar merging is implemented in `gradle-intellij-plugin` itself
        mustRunAfter(mergePluginJar)
        val enableBuildSearchableOptions = intellijRust.enableBuildSearchableOptions
        onlyIf { enableBuildSearchableOptions.get() }
    }

    withType<RunIdeTask>().configureEach {
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

    withType<PatchPluginXmlTask>().configureEach {
        pluginDescription.set(provider { file("description.html").readText() })
    }

    withType<PublishPluginTask>().configureEach {
        token.set(intellijRust.publishToken)
        channels.add(intellijRust.channel)
    }
}

tasks.withType<PrepareSandboxTask>().configureEach {
    dependsOn(tasks.compileNativeCode)

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

val buildEventsScheme by tasks.registering(RunIdeTask::class) {
    description = "Generate event scheme for Rust plugin FUS events to `plugin/build/eventScheme.json`"
    dependsOn(tasks.prepareSandbox)

    val eventSchemeJsonFile = layout.projectDirectory.file("eventScheme.json")
    outputs.file(eventSchemeJsonFile).withPropertyName("eventSchemeJsonFile")

    args(
        "buildEventsScheme",
        "--outputFile=${eventSchemeJsonFile.asFile.absoluteFile.invariantSeparatorsPath}",
        "--pluginId=org.rust.lang",
    )

    // BACKCOMPAT: 2023.1. Update value to 232 and this comment
    // `IDEA_BUILD_NUMBER` variable is used by `buildEventsScheme` task to write `buildNumber` to output json.
    // It will be used by TeamCity automation to set minimal IDE version for new events
    environment("IDEA_BUILD_NUMBER", "231")
}

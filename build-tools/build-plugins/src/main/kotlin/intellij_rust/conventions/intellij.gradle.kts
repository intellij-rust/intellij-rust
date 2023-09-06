package intellij_rust.conventions

import intellij_rust.internal.intellijRust
import intellij_rust.utilities.not
import org.jetbrains.intellij.tasks.PatchPluginXmlTask

/**
 * Conventions for IntelliJ plugin projects.
 *
 * Also applies the [intellij_rust.conventions.KotlinJvmPlugin] plugin.
 */

plugins {
    id("intellij_rust.conventions.base")
    id("intellij_rust.conventions.kotlin-jvm")
    id("org.jetbrains.grammarkit")
    id("org.jetbrains.intellij")
}

intellij {
    version.convention(intellijRust.baseVersion)
    downloadSources.convention(!intellijRust.isCI)
    updateSinceUntilBuild.convention(true)
    instrumentCode.convention(false)
    ideaDependencyCachePath.convention(dependencyCachePath)
    sandboxDir.convention("$buildDir/${intellijRust.baseIDE.get()}-sandbox-${intellijRust.platformVersion.get()}")
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


tasks.withType<PatchPluginXmlTask>().configureEach {
    sinceBuild.set(intellijRust.sinceBuild)
    untilBuild.set(intellijRust.untilBuild)
}

// All these tasks don't make sense for non-root subprojects
// Root project (i.e. `:plugin`) enables them itself if needed
tasks.runIde { enabled = false }
tasks.prepareSandbox { enabled = false }
tasks.buildSearchableOptions { enabled = false }

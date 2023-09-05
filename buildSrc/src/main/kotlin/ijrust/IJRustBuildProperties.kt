package ijrust

import javax.inject.Inject
import org.gradle.api.provider.*
import org.gradle.kotlin.dsl.*
import org.gradle.api.Project

abstract class IJRustBuildProperties @Inject constructor(
    private val providers: ProviderFactory
) {
    val isCI = providers.environmentVariable("CI").map { it.isNotBlank() }.orElse(false)
    val isTeamcity = providers.environmentVariable("TEAMCITY_VERSION").map { it.isNotBlank() }.orElse(false)

    val channel = prop("publishChannel")
    val platformVersion = prop("platformVersion").map { it.toInt() }
    val baseIDE = prop("baseIDE")
    val ideaVersion = prop("ideaVersion")
    val clionVersion = prop("clionVersion")
    val baseVersion: Provider<String> = baseIDE.map {
        when (it) {
            "idea" -> ideaVersion.get()
            "clion" -> clionVersion.get()
            else -> error("Unexpected IDE name: `$baseIDE`")
        }
    }

    val tomlPlugin = "org.toml.lang"
    val nativeDebugPlugin = prop("nativeDebugPlugin")
    val graziePlugin = "tanvd.grazi"
    val psiViewerPlugin = prop("psiViewerPlugin")
    val intelliLangPlugin = "org.intellij.intelliLang"
    val copyrightPlugin = "com.intellij.copyright"
    val javaPlugin = "com.intellij.java"
    val javaIdePlugin = "com.intellij.java.ide"
    val javaScriptPlugin = "JavaScript"
    val clionPlugins = listOf("com.intellij.cidr.base", "com.intellij.clion")
    val mlCompletionPlugin = "com.intellij.completion.ml.ranking"

    val compileNativeCodeTaskName = "compileNativeCode"

    val grammarKitFakePsiDeps = "grammar-kit-fake-psi-deps"

    val basePluginArchiveName = "intellij-rust"


    val publishToken = prop("publishToken")
    val sinceBuild = prop("sinceBuild")
    val untilBuild = prop("untilBuild")
    val compileNativeCode = prop("compileNativeCode").map { it.toBoolean() }
    val showStandardStreams = prop("showStandardStreams").map { it.toBoolean() }
    val enableBuildSearchableOptions = prop("enableBuildSearchableOptions").map { it.toBoolean() }

    val channelSuffix = channel.map { if (it.isBlank() || it == "stable") "" else "-$channel" }
    val versionSuffix = providers.zip(platformVersion, channelSuffix) { version, suffix ->
        "-$version$suffix"
    }
    val majorVersion = "0.4"
    val patchVersion = prop("patchVersion").map { it.toInt() }
    val buildNumber = prop("buildNumber")
    val version = "$majorVersion.$patchVersion.${buildNumber.get()}$versionSuffix"

    private fun prop(key: String) = providers.gradleProperty(key)

    companion object {

        /**
         * Retrieves the [dokkaBuild][IJRustBuildProperties] extension.
         */
        internal val Project.ijRustBuild: IJRustBuildProperties
            get() = extensions.getByType()

        /**
         * Configures the [ijRustBuild][IJRustBuildProperties] extension.
         */
        internal fun Project.ijRustBuild(configure: IJRustBuildProperties.() -> Unit) =
            extensions.configure(configure)
    }
}

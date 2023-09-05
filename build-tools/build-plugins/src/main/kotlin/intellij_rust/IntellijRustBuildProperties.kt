/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package intellij_rust

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject
import intellij_rust.utilities.*

abstract class IntellijRustBuildProperties @Inject constructor(
    private val providers: ProviderFactory,
) {
    val channel: Provider<String> = prop("publishChannel")
    val buildNumber: Provider<String> = prop("buildNumber")
    val platformVersion: Provider<Int> = prop("platformVersion").toInt()
    val baseIDE: Provider<String> = prop("baseIDE")
    val ideaVersion: Provider<String> = prop("ideaVersion")
    val clionVersion: Provider<String> = prop("clionVersion")
    val compileNativeCode: Provider<Boolean> = prop("compileNativeCode").toBoolean()
    val patchVersion: Provider<Int> = prop("patchVersion").toInt()
    val majorVersion: Provider<String> = prop("majorVersion")
    val channelSuffix: Provider<String> = channel.map { if (it.isBlank() || it == "stable") "" else "-$channel" }
    val versionSuffix: Provider<String> = providers.zip(platformVersion, channelSuffix) { platform, channel ->
        "-$platform$channel"
    }

    val fullVersion: Provider<String> =
        providers.provider { "${majorVersion.get()}.${patchVersion.get()}.${buildNumber.get()}${versionSuffix.get()}" }

    val baseVersion: Provider<String> =
        baseIDE.map { baseIDE ->
            when (baseIDE) {
                "idea" -> ideaVersion.get()
                "clion" -> clionVersion.get()
                else -> error("Unexpected IDE name: `$baseIDE`")
            }
        }

    val publishToken: Provider<String> = prop("publishToken")
    val sinceBuild: Provider<String> = prop("sinceBuild")
    val untilBuild: Provider<String> = prop("untilBuild")

    val clionPlugins = listOf("com.intellij.cidr.base", "com.intellij.clion")
    val copyrightPlugin = "com.intellij.copyright"
    val graziePlugin = "tanvd.grazi"
    val intelliLangPlugin = "org.intellij.intelliLang"
    val javaIdePlugin = "com.intellij.java.ide"
    val javaPlugin = "com.intellij.java"
    val javaScriptPlugin = "JavaScript"
    val mlCompletionPlugin = "com.intellij.completion.ml.ranking"
    val nativeDebugPlugin: Provider<String> = prop("nativeDebugPlugin")
    val psiViewerPlugin: Provider<String> = prop("psiViewerPlugin")
    val tomlPlugin = "org.toml.lang"
    val grammarKitFakePsiDeps = "grammar-kit-fake-psi-deps"

    val enableBuildSearchableOptions: Provider<Boolean> = prop("enableBuildSearchableOptions").toBoolean()
    val showTestStatus: Provider<Boolean> = prop("showTestStatus").toBoolean()
    val showStandardStreams: Provider<Boolean> = prop("showStandardStreams").toBoolean()
    val excludeTests: Provider<String> = prop("excludeTests").orElse("")

    val isCI: Provider<Boolean> = providers.environmentVariable("CI").toBoolean()
    val isTeamcity: Provider<Boolean> = providers.environmentVariable("TEAMCITY_VERSION").toBoolean()

    private fun prop(name: String): Provider<String> = providers.gradleProperty(name)
}

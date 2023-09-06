/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package intellij_rust

import intellij_rust.utilities.toBoolean
import intellij_rust.utilities.toInt
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

abstract class IntellijRustBuildProperties @Inject constructor(
    private val providers: ProviderFactory,
) {

    //region IntelliJ Platform properties
    val currentIntelliJPlatform: Provider<IntelliJPlatform> = prop("platformVersion")
        .toInt()
        .map { IntelliJPlatform.fromVersionNumber(it) }
    val ideaVersion: Provider<String> = currentIntelliJPlatform.map { it.ideaVersion }
    val clionVersion: Provider<String> = currentIntelliJPlatform.map { it.clionVersion }
    val sinceBuild: Provider<String> = currentIntelliJPlatform.map { it.sinceBuild }
    val untilBuild: Provider<String> = currentIntelliJPlatform.map { it.untilBuild }

    val channel: Provider<String> = prop("publishChannel")
    val buildNumber: Provider<String> = prop("buildNumber")
    val platformVersion: Provider<Int> = prop("platformVersion").toInt()
    val baseIDE: Provider<String> = prop("baseIDE")

    val baseVersion: Provider<String> =
        baseIDE.map { baseIDE ->
            when (baseIDE) {
                "idea" -> ideaVersion.get()
                "clion" -> clionVersion.get()
                else -> error("Unexpected IDE name: `$baseIDE`")
            }
        }
    //endregion


    //region IntelliJ Rust Plugin version
    val majorVersion: Provider<String> = prop("majorVersion")
    val patchVersion: Provider<Int> = prop("patchVersion").toInt()
    val channelSuffix: Provider<String> = channel.map { if (it.isBlank() || it == "stable") "" else "-$it" }.orElse("")
    val versionSuffix: Provider<String> = providers.zip(platformVersion, channelSuffix) { platform, channel ->
        "-$platform$channel"
    }

    val fullVersion: Provider<String> =
        providers.provider { "${majorVersion.get()}.${patchVersion.get()}.${buildNumber.get()}${versionSuffix.get()}" }
    //endregion


    //region publication properties
    val publishToken: Provider<String> = prop("publishToken")
    //endregion


    //region plugin IDs
    val clionPlugins = listOf("com.intellij.cidr.base", "com.intellij.clion")
    val copyrightPlugin = "com.intellij.copyright"
    val grammarKitFakePsiDeps = "grammar-kit-fake-psi-deps"
    val graziePlugin = "tanvd.grazi"
    val intelliLangPlugin = "org.intellij.intelliLang"
    val javaIdePlugin = "com.intellij.java.ide"
    val javaPlugin = "com.intellij.java"
    val javaScriptPlugin = "JavaScript"
    val mlCompletionPlugin = "com.intellij.completion.ml.ranking"
    val tomlPlugin = "org.toml.lang"

    val nativeDebugPlugin: Provider<String> = currentIntelliJPlatform.map { it.nativeDebugPlugin }
    val psiViewerPlugin: Provider<String> = currentIntelliJPlatform.map { it.psiViewerPlugin }
    //endregion


    //region build behaviour flags
    val compileNativeCode: Provider<Boolean> = prop("compileNativeCode").toBoolean()
    val enableBuildSearchableOptions: Provider<Boolean> = prop("enableBuildSearchableOptions").toBoolean()
    val showTestStatus: Provider<Boolean> = prop("showTestStatus").toBoolean()
    val showStandardStreams: Provider<Boolean> = prop("showStandardStreams").toBoolean()
    val excludeTests: Provider<String> = prop("excludeTests").orElse("")

    val isCI: Provider<Boolean> = providers.environmentVariable("CI").toBoolean()
    val isTeamcity: Provider<Boolean> = providers.environmentVariable("TEAMCITY_VERSION").toBoolean()
    //endregion


    private fun prop(name: String): Provider<String> = providers.gradleProperty(name)
}

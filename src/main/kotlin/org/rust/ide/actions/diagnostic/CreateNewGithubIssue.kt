/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.diagnostic

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.URLUtil
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.runconfig.hasCargoProject
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.ide.experiments.EnabledInStable
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.isRustFile
import org.rust.lang.core.resolve2.isNewResolveEnabled
import org.rust.openapiext.plugin
import org.rust.openapiext.virtualFile
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

class CreateNewGithubIssue : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.hasCargoProject == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val pluginVersion = plugin().version
        val toolchainVersion = project.cargoProjects.allProjects
            .asSequence()
            .mapNotNull { it.rustcInfo?.version }
            .firstOrNull()
            ?.displayText
        val ideNameAndVersion = ideNameAndVersion
        val os = SystemInfo.getOsNameAndVersion()
        val macroEngine = project.rustSettings.macroExpansionEngine.name.toLowerCase()
        val resolveEngine = if (project.isNewResolveEnabled) "new" else "old"
        val additionalExperimentalFeatures = additionalExperimentalFeatures
        val codeSnippet = e.getData(PlatformDataKeys.EDITOR)?.codeExample.orEmpty()

        val environmentInfo = buildEnvironmentInfo(
            pluginVersion,
            toolchainVersion,
            ideNameAndVersion,
            os,
            macroEngine,
            resolveEngine,
            additionalExperimentalFeatures
        )
        val body = ISSUE_TEMPLATE.format(environmentInfo, codeSnippet)
        val link = "https://github.com/intellij-rust/intellij-rust/issues/new?body=${URLUtil.encodeURIComponent(body)}"
        BrowserUtil.browse(link)
    }

    companion object {
        private val ISSUE_TEMPLATE = """
            <!--
            Hello and thank you for the issue!
            If you would like to report a bug, we have added some points below that you can fill out.
            Feel free to remove all the irrelevant text to request a new feature.
            -->

            ## Environment

            %s

            ## Problem description


            ## Steps to reproduce
            %s

            <!--
            Please include as much of your codebase as needed to reproduce the error.
            If the relevant files are large, please provide a link to a public repository or a [Gist](https://gist.github.com/).
            -->
        """.trimIndent()

        private fun buildEnvironmentInfo(
            pluginVersion: String,
            toolchainVersion: String?,
            ideNameAndVersion: String,
            os: String,
            macroEngine: String,
            resolveEngine: String,
            additionalExperimentalFeatures: String?,
        ): String = """
            * **IntelliJ Rust plugin version:** $pluginVersion
            * **Rust toolchain version:** $toolchainVersion
            * **IDE name and version:** $ideNameAndVersion
            * **Operating system:** $os
            * **Macro expansion engine:** $macroEngine
            * **Name resolution engine:** $resolveEngine
            ${additionalExperimentalFeatures?.let { "* **Additional experimental features:** $it" }.orEmpty()}    
        """.trimEnd().trimIndent()

        private val ideNameAndVersion: String
            get() {
                val appInfo = ApplicationInfo.getInstance()
                val appName = appInfo.fullApplicationName
                val editionName = ApplicationNamesInfo.getInstance().editionName
                val ideVersion = appInfo.build.toString()
                return buildString {
                    append(appName)
                    if (editionName != null) {
                        append(" ")
                        append(editionName)
                    }
                    append(" (")
                    append(ideVersion)
                    append(")")
                }
            }

        /** Collects additional (disabled in stable releases) experimental features enabled by user */
        private val additionalExperimentalFeatures: String?
            get() = with(Experiments.getInstance()) {
                RsExperiments::class.memberProperties
                    .filterNot { it.hasAnnotation<EnabledInStable>() }
                    .mapNotNull { it.call() as? String }
                    .filter { isFeatureEnabled(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
            }

        private val RustcVersion.displayText: String
            get() = buildString {
                append(semver.parsedVersion)
                if (commitHash != null) {
                    append(" (")
                    append(commitHash.take(9))
                    if (commitDate != null) {
                        append(" ")
                        append(commitDate)
                    }
                    append(")")
                }
                append(" ")
                append(host)
            }

        private val Editor.codeExample: String
            get() {
                if (document.virtualFile?.isRustFile != true) return ""
                val selectedCode = selectionModel.selectedText ?: return ""
                return "```rust\n$selectedCode\n```"
            }
    }
}

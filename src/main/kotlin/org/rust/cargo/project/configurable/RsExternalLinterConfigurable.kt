/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.toBinding
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.util.CargoCommandCompletionProvider
import org.rust.cargo.util.RsCommandLineEditor

class RsExternalLinterConfigurable(project: Project) : RsConfigurableBase(project, "Linters") {
    override fun createPanel(): DialogPanel = panel {
        row("External tool:") {
            comboBox(
                EnumComboBoxModel(ExternalLinter::class.java),
                state::externalLinter,
            ).comment("External tool to use for code analysis")
        }

        row("Additional arguments:") {
            RsCommandLineEditor(project, CargoCommandCompletionProvider(project.cargoProjects, "check ") { null })(CCFlags.growX)
                .comment("Additional arguments to pass to <b>cargo check</b> or <b>cargo clippy</b> command")
                .withBinding(
                    componentGet = { it.text },
                    componentSet = { component, value -> component.text = value },
                    modelBinding = state::externalLinterArguments.toBinding()
                )
        }

        row {
            checkBox(
                "Run external linter to analyze code on the fly",
                state::runExternalLinterOnTheFly,
                comment = """
                Enable external linter to add code highlighting based on the used linter result.
                Can be CPU-consuming
            """.trimIndent()
            )
        }
    }
}

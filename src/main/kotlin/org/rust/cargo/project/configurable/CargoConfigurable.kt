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

class CargoConfigurable(project: Project) : RsConfigurableBase(project, "Cargo") {

    private lateinit var externalLinterArguments: RsCommandLineEditor

    override fun createPanel(): DialogPanel {
        externalLinterArguments = RsCommandLineEditor(
            project,
            CargoCommandCompletionProvider(project.cargoProjects, "check ") { null }
        )
        return panel {
            row {
                checkBox(
                    "Automatically show first error in editor after a build failure",
                    state::autoShowErrorsInEditor
                )
            }
            row {
                checkBox(
                    "Update project automatically if Cargo.toml changes",
                    state::autoUpdateEnabled
                )
            }
            row {
                checkBox(
                    "Compile all project targets if possible",
                    state::compileAllTargets,
                    comment = "Pass <b>--target-all</b> option to Сargo <b>build</b>/<b>check</b> command"
                )
            }
            row {
                checkBox(
                    "Offline mode",
                    state::useOffline,
                    comment = "Pass <b>--offline</b> option to Cargo not to perform network requests"
                )
            }

            titledRow("External Linter") {
                subRowIndent = 0
                row("External tool:") {
                    comboBox(
                        EnumComboBoxModel(ExternalLinter::class.java),
                        state::externalLinter,
                    ).comment("External tool to use for code analysis")
                }

                row("Additional arguments:") {
                    externalLinterArguments(CCFlags.growX)
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
    }
}

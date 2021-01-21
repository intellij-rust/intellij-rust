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
import org.rust.RsBundle
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
                    RsBundle.message("cargo.update.project.automatically.if.cargo.toml.changes"),
                    state::autoUpdateEnabled
                )
            }
            row {
                checkBox(
                    RsBundle.message("cargo.compile.all.project.targets.if.possible"),
                    state::compileAllTargets,
                    comment = RsBundle.message("cargo.compile.all.project.targets.if.possible.comment")
                )
            }
            row {
                checkBox(
                    RsBundle.message("cargo.offline.mode"),
                    state::useOffline,
                    comment = RsBundle.message("cargo.offline.mode.comment")
                )
            }

            titledRow(RsBundle.message("cargo.external.linter")) {
                subRowIndent = 0
                row(RsBundle.message("cargo.external.linter.tool")) {
                    comboBox(
                        EnumComboBoxModel(ExternalLinter::class.java),
                        state::externalLinter,
                    ).comment(RsBundle.message("cargo.external.linter.tool.comment"))
                }

                row(RsBundle.message("cargo.external.linter.tool.arguments")) {
                    externalLinterArguments(CCFlags.growX)
                        .comment(RsBundle.message("cargo.external.linter.tool.arguments.comment"))
                        .withBinding(
                            componentGet = { it.text },
                            componentSet = { component, value -> component.text = value },
                            modelBinding = state::externalLinterArguments.toBinding()
                        )
                }
                row {
                    checkBox(
                        RsBundle.message("cargo.run.external.linter"),
                        state::runExternalLinterOnTheFly,
                        comment = RsBundle.message("cargo.run.external.linter.comment")
                    )
                }
            }
        }
    }
}

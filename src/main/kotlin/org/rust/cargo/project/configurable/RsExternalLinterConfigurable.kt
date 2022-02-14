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

class RsExternalLinterConfigurable(project: Project) : RsConfigurableBase(project, RsBundle.message("settings.rust.external.linters.name")) {
    override fun createPanel(): DialogPanel = panel {
        row(RsBundle.message("settings.rust.external.linters.tool.label")) {
            comboBox(
                EnumComboBoxModel(ExternalLinter::class.java),
                state::externalLinter,
            ).comment(RsBundle.message("settings.rust.external.linters.tool.comment"))
        }

        row(RsBundle.message("settings.rust.external.linters.additional.arguments.label")) {
            RsCommandLineEditor(project, CargoCommandCompletionProvider(project.cargoProjects, "check ") { null })(CCFlags.growX)
                .comment(RsBundle.message("settings.rust.external.linters.additional.arguments.comment"))
                .withBinding(
                    componentGet = { it.text },
                    componentSet = { component, value -> component.text = value },
                    modelBinding = state::externalLinterArguments.toBinding()
                )
        }

        row {
            checkBox(
                RsBundle.message("settings.rust.external.linters.on.the.fly.label"),
                state::runExternalLinterOnTheFly,
                comment = RsBundle.message("settings.rust.external.linters.on.the.fly.comment")
            )
        }
    }
}

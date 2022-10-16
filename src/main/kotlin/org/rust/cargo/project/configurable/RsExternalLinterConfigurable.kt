/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.dsl.builder.*
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RsExternalLinterProjectSettingsService
import org.rust.cargo.project.settings.externalLinterSettings
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.util.CargoCommandCompletionProvider
import org.rust.cargo.util.RsCommandLineEditor
import org.rust.openapiext.fullWidthCell

class RsExternalLinterConfigurable(project: Project) : RsConfigurableBase(project, RsBundle.message("settings.rust.external.linters.name")) {
    private val settings: RsExternalLinterProjectSettingsService = project.externalLinterSettings

    private val additionalArguments: RsCommandLineEditor =
        RsCommandLineEditor(project, CargoCommandCompletionProvider(project.cargoProjects, "check ") { null })

    override fun createPanel(): DialogPanel = panel {
        val state = settings.state.copy()

        row(RsBundle.message("settings.rust.external.linters.tool.label")) {
            comboBox(EnumComboBoxModel(ExternalLinter::class.java))
                .comment(RsBundle.message("settings.rust.external.linters.tool.comment"))
                .bindItem(state::tool.toNullableProperty())
        }

        row(RsBundle.message("settings.rust.external.linters.additional.arguments.label")) {
            fullWidthCell(additionalArguments)
                .resizableColumn()
                .comment(RsBundle.message("settings.rust.external.linters.additional.arguments.comment"))
                .bind(
                    componentGet = { it.text },
                    componentSet = { component, value -> component.text = value },
                    prop = state::additionalArguments.toMutableProperty()
                )
        }

        row {
            checkBox(RsBundle.message("settings.rust.external.linters.on.the.fly.label"))
                .comment(RsBundle.message("settings.rust.external.linters.on.the.fly.comment"))
                .bindSelected(state::runOnTheFly)
        }

        onApply {
            settings.modify {
                it.tool = state.tool
                it.additionalArguments = state.additionalArguments
                it.runOnTheFly = state.runOnTheFly
            }
        }
    }
}

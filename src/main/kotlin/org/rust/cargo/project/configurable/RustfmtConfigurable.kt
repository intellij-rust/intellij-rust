/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import org.rust.RsBundle
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.toolchain.RustChannel
import org.rust.openapiext.fullWidthCell
import javax.swing.JLabel

class RustfmtConfigurable(project: Project) : RsConfigurableBase(project, RsBundle.message("settings.rust.rustfmt.name")) {
    private val additionalArguments: RawCommandLineEditor = RawCommandLineEditor()

    private val channelLabel: JLabel = Label(RsBundle.message("settings.rust.rustfmt.channel.label"))
    private val channel: ComboBox<RustChannel> = ComboBox<RustChannel>().apply {
        RustChannel.values()
            .sortedBy { it.index }
            .forEach { addItem(it) }
    }

    private val environmentVariables: EnvironmentVariablesComponent = EnvironmentVariablesComponent()

    override fun createPanel(): DialogPanel = panel {
        val settings = project.rustfmtSettings
        val state = settings.state.copy()

        row(RsBundle.message("settings.rust.rustfmt.additional.arguments.label")) {
            fullWidthCell(additionalArguments)
                .resizableColumn()
                .comment(RsBundle.message("settings.rust.rustfmt.additional.arguments.comment"))
                .bind(
                    componentGet = { it.text },
                    componentSet = { component, value -> component.text = value },
                    prop = state::additionalArguments.toMutableProperty()
                )

            channelLabel.labelFor = channel
            cell(channelLabel)
            cell(channel)
                .bind(
                    componentGet = { it.item },
                    componentSet = { component, value -> component.item = value },
                    prop = state::channel.toMutableProperty()
                )
        }

        row(environmentVariables.label) {
            fullWidthCell(environmentVariables)
                .bind(
                    componentGet = { it.envs },
                    componentSet = { component, value -> component.envs = value },
                    prop = state::envs.toMutableProperty()
                )
        }

        row { checkBox(RsBundle.message("settings.rust.rustfmt.builtin.formatter.label")).bindSelected(state::useRustfmt) }
        row { checkBox(RsBundle.message("settings.rust.rustfmt.run.on.save.label")).bindSelected(state::runRustfmtOnSave) }

        onApply {
            settings.modify {
                it.additionalArguments = state.additionalArguments
                it.channel = state.channel
                it.envs = state.envs
                it.useRustfmt = state.useRustfmt
                it.runRustfmtOnSave = state.runRustfmtOnSave
            }
        }
    }
}

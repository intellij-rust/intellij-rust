/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.ide.DataManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import org.rust.RsBundle
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.toolchain.RustChannel
import org.rust.openapiext.fullWidthCell

class RustfmtConfigurable(project: Project) : BoundConfigurable(RsBundle.message("settings.rust.rustfmt.name")) {
    private val settings = project.rustfmtSettings

    private val additionalArguments = RawCommandLineEditor()

    private val channelLabel = Label(RsBundle.message("settings.rust.rustfmt.channel.label"))
    private val channel = ComboBox<RustChannel>().apply {
        RustChannel.values()
            .sortedBy { it.index }
            .forEach { addItem(it) }
    }

    private val environmentVariables = EnvironmentVariablesComponent()

    override fun createPanel(): DialogPanel = panel {
        group(indent = false) {
            row(RsBundle.message("settings.rust.rustfmt.additional.arguments.label")) {
                fullWidthCell(additionalArguments)
                    .resizableColumn()
                    .comment(RsBundle.message("settings.rust.rustfmt.additional.arguments.comment"))
                    .bind(
                        componentGet = { it.text },
                        componentSet = { component, value -> component.text = value },
                        prop = settings.state::additionalArguments.toMutableProperty()
                    )

                channelLabel.labelFor = channel
                cell(channelLabel)
                cell(channel)
                    .bind(
                        componentGet = { it.item },
                        componentSet = { component, value -> component.item = value },
                        prop = settings.state::channel.toMutableProperty()
                    )
            }

            row(environmentVariables.label) {
                fullWidthCell(environmentVariables)
                    .bind(
                        componentGet = { it.envs },
                        componentSet = { component, value -> component.envs = value },
                        prop = settings.state::envs.toMutableProperty()
                    )
            }
        }.bottomGap(BottomGap.MEDIUM) // TODO: do we really need it?

        row {
            checkBox(RsBundle.message("settings.rust.rustfmt.builtin.formatter.label"))
                .comment(RsBundle.message("settings.rust.rustfmt.builtin.formatter.label.comment"))
                .bindSelected(settings.state::useRustfmt)
        }

        row {
            link(RsBundle.message("settings.rust.rustfmt.actions.on.save")) {
                DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
                    val settings = Settings.KEY.getData(context)
                    settings?.select(settings.find("actions.on.save"))
                }
            }
        }
    }
}

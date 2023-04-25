/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject.ui

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.Link
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.JBUI
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.cargo
import org.rust.ide.newProject.ConfigurationData
import org.rust.ide.newProject.RsCustomTemplate
import org.rust.ide.newProject.RsGenericTemplate
import org.rust.ide.newProject.RsProjectTemplate
import org.rust.ide.newProject.state.RsUserTemplatesState
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.UiDebouncer
import org.rust.openapiext.fullWidthCell
import org.rust.stdext.unwrapOrThrow
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel
import kotlin.math.min

class RsNewProjectPanel(
    private val showProjectTypeSelection: Boolean,
    cargoProjectDir: Path = Paths.get("."),
    private val updateListener: (() -> Unit)? = null
) : Disposable {

    private val rustProjectSettings = RustProjectSettingsPanel(cargoProjectDir, updateListener)

    private val cargo: Cargo?
        get() = rustProjectSettings.data.toolchain?.cargo()

    private val defaultTemplates: List<RsProjectTemplate> = listOf(
        RsGenericTemplate.CargoBinaryTemplate,
        RsGenericTemplate.CargoLibraryTemplate,
        RsCustomTemplate.ProcMacroTemplate,
        RsCustomTemplate.WasmPackTemplate
    )

    private val userTemplates: List<RsCustomTemplate>
        get() = RsUserTemplatesState.getInstance().templates.map {
            RsCustomTemplate(it.name, it.url)
        }

    private val templateListModel: DefaultListModel<RsProjectTemplate> =
        JBList.createDefaultListModel(defaultTemplates + userTemplates)

    private val templateList: JBList<RsProjectTemplate> = JBList(templateListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        selectedIndex = 0
        addListSelectionListener { update() }
        cellRenderer = object : ColoredListCellRenderer<RsProjectTemplate>() {
            override fun customizeCellRenderer(
                list: JList<out RsProjectTemplate>,
                value: RsProjectTemplate,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                icon = value.icon
                append(value.name)

                if (value is RsCustomTemplate) {
                    append(" ")
                    append(value.shortLink, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }
    }

    private val selectedTemplate: RsProjectTemplate
        get() = templateList.selectedValue

    private val templateToolbar: ToolbarDecorator = ToolbarDecorator.createDecorator(templateList)
        .setToolbarPosition(ActionToolbarPosition.BOTTOM)
        .setPreferredSize(JBUI.size(0, 125))
        .disableUpDownActions()
        .setAddAction {
            AddUserTemplateDialog().show()
            updateTemplatesList()
        }
        .setRemoveAction {
            val customTemplate = selectedTemplate as? RsCustomTemplate ?: return@setRemoveAction
            RsUserTemplatesState.getInstance().templates
                .removeIf { it.name == customTemplate.name }
            updateTemplatesList()
        }
        .setRemoveActionUpdater { selectedTemplate !in defaultTemplates }

    private var needInstallCargoGenerate = false

    @Suppress("DialogTitleCapitalization")
    private val downloadCargoGenerateLink = Link("Install cargo-generate using Cargo") {
        val cargo = cargo ?: return@Link

        object : Task.Modal(null, "Installing cargo-generate", true) {
            var exitCode: Int = Int.MIN_VALUE

            override fun onFinished() {
                if (exitCode != 0) {
                    templateList.showBalloon("Failed to install cargo-generate", MessageType.ERROR, this@RsNewProjectPanel)
                }

                update()
            }

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                cargo.installCargoGenerate(this@RsNewProjectPanel, listener = object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        indicator.text = "Installing using Cargo..."
                        indicator.text2 = event.text.trim()
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        exitCode = event.exitCode
                    }
                }).unwrapOrThrow()
            }
        }.queue()
    }.apply { isVisible = false }

    private val updateDebouncer = UiDebouncer(this)

    val data: ConfigurationData get() = ConfigurationData(rustProjectSettings.data, selectedTemplate)

    fun attachTo(panel: Panel) = with(panel) {
        rustProjectSettings.attachTo(this)

        if (showProjectTypeSelection) {
            groupRowsRange(title = "Project Template", indent = false) {
                row {
                    resizableRow()
                    fullWidthCell(templateToolbar.createPanel())
                        .verticalAlign(VerticalAlign.FILL)
                }
                row {
                    cell(downloadCargoGenerateLink)
                }
            }
        }

        update()
    }

    fun update() {
        updateDebouncer.run(
            onPooledThread = {
                when (selectedTemplate) {
                    is RsGenericTemplate -> false
                    is RsCustomTemplate -> cargo?.checkNeedInstallCargoGenerate() ?: false
                }
            },
            onUiThread = { needInstall ->
                downloadCargoGenerateLink.isVisible = needInstall
                needInstallCargoGenerate = needInstall
                updateListener?.invoke()
            }
        )
    }

    private fun updateTemplatesList() {
        val index: Int = templateList.selectedIndex

        with(templateListModel) {
            removeAllElements()
            defaultTemplates.forEach(::addElement)
            userTemplates.forEach(::addElement)
        }

        templateList.selectedIndex = min(index, templateList.itemsCount - 1)
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        rustProjectSettings.validateSettings()

        if (needInstallCargoGenerate) {
            @Suppress("DialogTitleCapitalization")
            throw ConfigurationException("cargo-generate is needed to create a project from a custom template")
        }
    }

    override fun dispose() {
        Disposer.dispose(rustProjectSettings)
    }
}

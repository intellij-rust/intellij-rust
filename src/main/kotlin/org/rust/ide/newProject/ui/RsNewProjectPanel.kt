/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject.ui

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.Link
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.Cargo
import org.rust.ide.newProject.ConfigurationData
import org.rust.ide.newProject.RsCustomTemplate
import org.rust.ide.newProject.RsGenericTemplate
import org.rust.ide.newProject.RsProjectTemplate
import org.rust.ide.ui.RsLayoutBuilder
import org.rust.openapiext.UiDebouncer
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

class RsNewProjectPanel(
    private val showProjectTypeSelection: Boolean,
    private val updateListener: (() -> Unit)? = null
) : Disposable {

    private val rustProjectSettings = RustProjectSettingsPanel(updateListener = updateListener)

    private val cargo: Cargo?
        get() = rustProjectSettings.data.toolchain?.rawCargo()

    private val templateList = JBList(
        RsGenericTemplate("Binary (application)", true),
        RsGenericTemplate("Library", false),
        RsCustomTemplate("WebAssembly Lib", "https://github.com/rustwasm/wasm-pack-template", false)
    ).apply {
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

    private val templatePane = JBScrollPane(templateList).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    private var needInstallCargoGenerate = false
    private val downloadCargoGenerateLink = Link("Install cargo-generate using Cargo", action = {
        val cargo = cargo ?: return@Link

        object : Task.Modal(null, "Installing cargo-generate", true) {
            var exitCode = 0

            override fun onSuccess() {
                if (exitCode != 0) {
                    PopupUtil.showBalloonForComponent(
                        templatePane,
                        "Failed to install cargo-generate",
                        MessageType.ERROR,
                        true,
                        this@RsNewProjectPanel
                    )
                }

                update()
            }

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                cargo.installCargoGenerate(this@RsNewProjectPanel, listener = object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        indicator.text = "Installing using Cargo..."
                        indicator.text2 = event.text.trim()
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        exitCode = event.exitCode
                    }

                    override fun startNotified(event: ProcessEvent) {}

                })
            }
        }.queue()
    }).apply { isVisible = false }

    private val updateDebouncer = UiDebouncer(this)

    val data: ConfigurationData get() = ConfigurationData(rustProjectSettings.data, selectedTemplate)

    fun attachTo(layout: RsLayoutBuilder) = with(layout) {
        rustProjectSettings.attachTo(this)

        if (showProjectTypeSelection) {
            component(JBLabel("Project template:"))
            component(templatePane)
            component(downloadCargoGenerateLink)
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

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        rustProjectSettings.validateSettings()

        if (needInstallCargoGenerate) {
            throw ConfigurationException("cargo-generate is needed to create a project from a custom template")
        }
    }

    override fun dispose() {
        Disposer.dispose(rustProjectSettings)
    }
}

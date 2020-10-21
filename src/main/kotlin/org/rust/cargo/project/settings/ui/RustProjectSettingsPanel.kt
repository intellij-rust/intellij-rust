/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel.Companion.createSdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.ui.ComboboxWithBrowseButton
import com.intellij.ui.layout.LayoutBuilder
import org.rust.cargo.project.configurable.RsConfigurableToolchainList
import org.rust.ide.sdk.RsSdkComparator
import org.rust.ide.sdk.RsSdkDetailsDialog
import org.rust.ide.sdk.RsSdkType
import org.rust.ide.sdk.toolchain
import java.awt.event.ItemEvent

class RustProjectSettingsPanel(
    private val project: Project? = null,
    private val updateListener: (() -> Unit)? = null
) : Disposable {
    private val toolchainList = RsConfigurableToolchainList.getInstance(project)

    private val sdkComboBox: SdkComboBox = run {
        val model = createSdkComboBoxModel(
            effectiveProject,
            toolchainList.model,
            sdkTypeFilter = { it is RsSdkType }
        )
        SdkComboBox(model).apply {
            addItemListener { event ->
                if (event.stateChange == ItemEvent.SELECTED) {
                    onSdkSelected()
                }
            }
        }
    }

    private val effectiveProject: Project
        get() = project ?: ProjectManager.getInstance().defaultProject

    var sdk: Sdk?
        get() = sdkComboBox.getSelectedSdk()
        set(value) {
            if (value != null) {
                sdkComboBox.setSelectedSdk(value)
            } else {
                sdkComboBox.selectedItem = sdkComboBox.showNoneSdkItem()
            }
        }

    init {
        preselectSdk()
    }

    fun attachTo(layout: LayoutBuilder) = with(layout) {
        val comboBox = ComboboxWithBrowseButton(sdkComboBox).apply {
            addActionListener { onShowAllSelected() }
        }
        row("Project Toolchain:") { comboBox() }
    }

    @Throws(ConfigurationException::class)
    fun validateSettings(sdkRequired: Boolean) {
        val sdk = sdk
        val toolchain = sdk?.toolchain
        when {
            sdk == null && sdkRequired ->
                throw ConfigurationException("No toolchain specified")
            sdk == null ->
                return
            toolchain == null ->
                throw ConfigurationException("Invalid toolchain")
            !toolchain.looksLikeValidToolchain() ->
                throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        }
    }

    override fun dispose() {
        toolchainList.disposeModel()
    }

    private fun buildAllSdksDialog(): RsSdkDetailsDialog =
        RsSdkDetailsDialog(
            project,
            toolchainList,
            selectedSdkCallback = { selectedSdk ->
                sdk = selectedSdk ?: sdk
            },
            cancelCallback = { reset ->
                if (reset) {
                    sdk = sdk
                }
            }
        )

    private fun onSdkSelected() {
        updateListener?.invoke()

        // Save SDK from `Detected SDKs` list
        invokeLater {
            runWriteAction {
                try {
                    toolchainList.model.apply(null, true)
                } catch (e: ConfigurationException) {
                    LOG.error(e)
                }
            }
        }
    }

    private fun onShowAllSelected() {
        buildAllSdksDialog().show()
    }

    private fun preselectSdk() {
        sdkComboBox.reloadModel()

        val model = sdkComboBox.model
        sdk = (0 until model.size).asSequence()
            .map { i -> model.getElementAt(i) }
            .filterIsInstance<SdkListItem.SdkItem>()
            .map { it.sdk }
            .sortedWith(RsSdkComparator)
            .firstOrNull()
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(RustProjectSettingsPanel::class.java)
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.util.NullableConsumer
import com.intellij.util.ui.JBUI
import com.intellij.webcore.packaging.PackagesNotificationPanel
import org.rust.ide.sdk.RsSdkType
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import java.util.*
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class RsActiveSdkConfigurable private constructor(private val project: Project, private val module: Module?) : UnnamedConfigurable {
    private val toolchainList: RsConfigurableToolchainList
    private val projectSdksModel: ProjectSdksModel
    private val mainPanel: JPanel
    private val sdkCombo: ComboBox<Any>
    private val binariesPanel: CargoInstalledBinariesPanel
    private val disposable: Disposable?

    private val originalSelectedSdk: Sdk?
        get() = editableSelectedSdk?.let { projectSdksModel.findSdk(it) }

    private val editableSelectedSdk: Sdk?
        get() = sdkCombo.selectedItem as Sdk

    protected var sdk: Sdk?
        get() {
            if (module == null) {
                return ProjectRootManager.getInstance(project).projectSdk
            }
            val rootManager = ModuleRootManager.getInstance(module)
            return rootManager.sdk
        }
        set(item) {
            RsSdkExtKt.setPythonSdk(project, item)
            if (module != null) {
                RsSdkExtKt.setPythonSdk(module, item)
            }
        }

    constructor(project: Project) : this(project, null)

    constructor(module: Module) : this(module.project, module)

    init {

        sdkCombo = buildSdkComboBox(Runnable { this.onShowAllSelected() }, Runnable { this.onSdkSelected() })

        val packagesNotificationPanel = PackagesNotificationPanel()
        binariesPanel = CargoInstalledBinariesPanel(project, packagesNotificationPanel)

        val customizer = buildCustomizer()
        disposable = customizer?.second

        val detailsButton = buildDetailsButton(sdkCombo, Consumer { this.onShowDetailsClicked(it) })

        mainPanel = buildPanel(project, sdkCombo, detailsButton, binariesPanel, packagesNotificationPanel, customizer)

        toolchainList = RsConfigurableToolchainList.getInstance(project)
        projectSdksModel = toolchainList.model
    }

    private fun onShowAllSelected() {
        buildAllSdksDialog().show()
    }

    private fun onSdkSelected() {
        val sdk = originalSelectedSdk
        val packageManagers = RsPackageManagers.getInstance()
        binariesPanel.updatePackages(if (sdk != null) packageManagers.getManagementService(project, sdk) else null)
        binariesPanel.updateNotifications(sdk)
    }

    private fun onShowDetailsClicked(detailsButton: JButton) {
        RsSdkDetailsStep.show(project, module, projectSdksModel.sdks, buildAllSdksDialog(), mainPanel,
            detailsButton.locationOnScreen, SdkAddedCallback())
    }

    private fun buildAllSdksDialog(): RsSdkDetailsDialog {
        return RsSdkDetailsDialog(
            project,
            module,
            { selectedSdk ->
                if (selectedSdk != null) {
                    updateSdkListAndSelect(selectedSdk)
                } else {
                    // do not use `getOriginalSelectedSdk()` here since `model` won't find original sdk for selected item due to applying
                    val currentSelectedSdk = editableSelectedSdk

                    if (currentSelectedSdk != null && projectSdksModel.findSdk(currentSelectedSdk.name) != null) {
                        // nothing has been selected but previously selected sdk still exists, stay with it
                        updateSdkListAndSelect(currentSelectedSdk)
                    } else {
                        // nothing has been selected but previously selected sdk removed, switch to `No interpreter`
                        updateSdkListAndSelect(null)
                    }
                }
            },
            { reset ->
                // data is invalidated on `model` resetting so we need to reload sdks to not stuck with outdated ones
                // do not use `getOriginalSelectedSdk()` here since `model` won't find original sdk for selected item due to resetting
                if (reset) updateSdkListAndSelect(editableSelectedSdk)
            }
        )
    }

    override fun createComponent(): JComponent? {
        return mainPanel
    }

    override fun isModified(): Boolean {
        return !Comparing.equal(sdk, originalSelectedSdk)
    }

    override fun apply() {
        val selectedSdk = originalSelectedSdk
        if (selectedSdk != null) {
            (selectedSdk.sdkType as RsSdkType).setupSdkPaths(selectedSdk)
        }
        sdk = selectedSdk
    }

    override fun reset() {
        updateSdkListAndSelect(sdk)
    }

    private fun updateSdkListAndSelect(selectedSdk: Sdk?) {
        val allPythonSdks = toolchainList.getAllRustSdks(project)

        val items = ArrayList<Any>()
        items.add(null)

        val moduleSdksByTypes = groupModuleSdksByTypes(allPythonSdks, module, ???({ RsSdkUtil.isInvalid() }))

        val renderedSdkTypes = RsRenderedSdkType.values()
        for (i in renderedSdkTypes.indices) {
            val currentSdkType = renderedSdkTypes[i]

            if (moduleSdksByTypes.containsKey(currentSdkType)) {
                if (i != 0) items.add(RsSdkListCellRenderer.SEPARATOR)
                items.addAll(moduleSdksByTypes.get(currentSdkType))
            }
        }

        items.add(RsSdkListCellRenderer.SEPARATOR)
        items.add(SHOW_ALL)

        sdkCombo.setRenderer(RsSdkListCellRenderer(null))
        val selection = if (selectedSdk == null) null else projectSdksModel.findSdk(selectedSdk.name)
        sdkCombo.setModel(CollectionComboBoxModel(items, selection))
        onSdkSelected()
    }

    override fun disposeUIResources() {
        toolchainList.disposeModel()
        if (disposable != null) {
            Disposer.dispose(disposable)
        }
    }

    private inner class SdkAddedCallback : NullableConsumer<Sdk> {
        override fun consume(sdk: Sdk?) {
            if (sdk != null && projectSdksModel.findSdk(sdk.name) == null) {
                projectSdksModel.addSdk(sdk)
                try {
                    projectSdksModel.apply(null, true)
                } catch (e: ConfigurationException) {
                    LOG.error(e)
                }

                updateSdkListAndSelect(sdk)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(RsActiveSdkConfigurable::class.java)

        private const val SHOW_ALL: String = "Show All..."

        private fun buildSdkComboBox(onShowAllSelected: Runnable, onSdkSelected: Runnable): ComboBox<Any> {
            val result = object : ComboBox<Any>() {
                override fun setSelectedItem(item: Any) {
                    if (SHOW_ALL == item) {
                        onShowAllSelected.run()
                    } else if (!RsSdkListCellRenderer.SEPARATOR.equals(item)) {
                        super.setSelectedItem(item)
                    }
                }
            }

            result.addItemListener { e -> if (e.stateChange == ItemEvent.SELECTED) onSdkSelected.run() }

            ComboboxSpeedSearch(result)
            result.preferredSize = result.preferredSize // this line allows making `result` resizable
            return result
        }

        private fun buildCustomizer(): Pair<RsCustomSdkUiProvider, Disposable>? {
            val customUiProvider = RsCustomSdkUiProvider.getInstance()
            return if (customUiProvider == null) null else Pair<RsCustomSdkUiProvider, Disposable>(customUiProvider, Disposer.newDisposable())
        }

        private fun buildDetailsButton(sdkComboBox: ComboBox<*>, onShowDetails: Consumer<JButton>): JButton {
            val result = FixedSizeButton(sdkComboBox.preferredSize.height)
            result.icon = AllIcons.General.GearPlain
            result.addActionListener { e -> onShowDetails.accept(result) }
            return result
        }

        private fun buildPanel(project: Project,
                               sdkComboBox: ComboBox<*>,
                               detailsButton: JButton,
                               installedPackagesPanel: CargoInstalledBinariesPanel,
                               packagesNotificationPanel: PackagesNotificationPanel,
                               customizer: Pair<RsCustomSdkUiProvider, Disposable>?): JPanel {
            val result = JPanel(GridBagLayout())

            val c = GridBagConstraints()
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = JBUI.insets(2)

            c.gridx = 0
            c.gridy = 0
            result.add(JLabel("Project Toolchain:"), c)

            c.gridx = 1
            c.gridy = 0
            c.weightx = 0.1
            result.add(sdkComboBox, c)

            c.insets = JBUI.insets(2, 0, 2, 2)
            c.gridx = 2
            c.gridy = 0
            c.weightx = 0.0
            result.add(detailsButton, c)

            customizer?.first.customizeActiveSdkPanel(project, sdkComboBox, result, c, customizer.second)

            c.insets = JBUI.insets(2, 2, 0, 2)
            c.gridx = 0
            c.gridy++
            c.gridwidth = 3
            c.weightx = 0.0
            result.add(JLabel("  "), c)

            c.gridx = 0
            c.gridy++
            c.weighty = 1.0
            c.gridwidth = 3
            c.gridheight = GridBagConstraints.RELATIVE
            c.fill = GridBagConstraints.BOTH
            result.add(installedPackagesPanel, c)

            c.gridheight = GridBagConstraints.REMAINDER
            c.gridx = 0
            c.gridy++
            c.gridwidth = 3
            c.weighty = 0.0
            c.fill = GridBagConstraints.HORIZONTAL
            c.anchor = GridBagConstraints.SOUTH

            result.add(packagesNotificationPanel.component, c)

            return result
        }
    }
}

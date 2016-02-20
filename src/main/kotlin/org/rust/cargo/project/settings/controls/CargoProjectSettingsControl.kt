package org.rust.cargo.project.settings.controls

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Conditions
import com.intellij.ui.components.JBLabel
import org.rust.cargo.project.RustSdkType
import org.rust.cargo.project.settings.CargoProjectSettings
import java.awt.GridBagConstraints
import javax.swing.JButton

class CargoProjectSettingsControl(settings: CargoProjectSettings)
    : AbstractExternalProjectSettingsControl<CargoProjectSettings>(settings) {

    private lateinit var sdkComboBox: JdkComboBox

    private lateinit var sdkModel: ProjectSdksModel

    override fun fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {
        sdkModel = ProjectSdksModel()
        sdkModel.reset(project)

        sdkComboBox = JdkComboBox(sdkModel, Conditions.equalTo(RustSdkType.INSTANCE))
        sdkComboBox.setSetupButton(JButton("New..."), project, sdkModel, null, null, false)

        with(content) {
            add(JBLabel("Rust SDK:"), ExternalSystemUiUtil.getLabelConstraints(indentLevel))
            add(sdkComboBox, ExternalSystemUiUtil.getFillLineConstraints(0).coverLine(GridBagConstraints.RELATIVE))
            add(sdkComboBox.setUpButton, ExternalSystemUiUtil.getFillLineConstraints(0))
        }
    }

    override fun resetExtraSettings(isDefaultModuleCreation: Boolean) {
        sdkComboBox.reloadModel(JdkComboBox.NoneJdkComboBoxItem(), project)
    }

    override fun validate(settings: CargoProjectSettings): Boolean {
        if (currentCargoHome == null) {
            throw ConfigurationException("Select a Rust SDK")
        }
        return true
    }

    override fun applyExtraSettings(settings: CargoProjectSettings) {
        settings.sdkName = currentSdkName
        sdkModel.apply()
    }

    override fun isExtraSettingModified(): Boolean = initialSettings.sdkName != currentSdkName

    private val currentCargoHome: String? get() = sdkComboBox.selectedJdk?.homePath

    private val currentSdkName: String? get() = sdkComboBox.selectedJdk?.name
}

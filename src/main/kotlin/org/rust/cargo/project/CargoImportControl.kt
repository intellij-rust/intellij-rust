package org.rust.cargo.project

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import org.rust.cargo.project.settings.CargoProjectSettings
import org.rust.cargo.project.settings.CargoProjectSettingsListener
import org.rust.cargo.project.settings.CargoSettings
import org.rust.cargo.project.settings.controls.CargoProjectSettingsControl

class CargoImportControl
    : AbstractImportFromExternalSystemControl<CargoProjectSettings, CargoProjectSettingsListener, CargoSettings>(
        CargoProjectSystem.ID,
        CargoSettings(ProjectManager.getInstance().defaultProject),
        ServiceManager.getService(ProjectManager.getInstance().defaultProject, CargoProjectSettings::class.java),
        /* showProjectFormatPanel = */ true) {

    override fun onLinkedProjectPathChange(s: String) {
    }

    override fun createProjectSettingsControl(settings: CargoProjectSettings): ExternalSystemSettingsControl<CargoProjectSettings> {
        return CargoProjectSettingsControl(settings)
    }

    override fun createSystemSettingsControl(systemSettings: CargoSettings): ExternalSystemSettingsControl<CargoSettings>? {
        return object : ExternalSystemSettingsControl<CargoSettings> {
            override fun fillUi(paintAwarePanel: PaintAwarePanel, identLevel: Int) {}

            override fun reset() {}

            override fun isModified(): Boolean {
                return false
            }

            override fun apply(settings: CargoSettings) {}

            @Throws(ConfigurationException::class)
            override fun validate(settings: CargoSettings): Boolean {
                return true
            }

            override fun disposeUIResources() {}

            override fun showUi(show: Boolean) {
            }
        }
    }
}

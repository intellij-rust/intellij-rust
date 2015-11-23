package org.rust.cargo.project.settings.controls

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import org.rust.cargo.project.settings.CargoProjectSettings
import org.rust.cargo.project.settings.controls.builders.CargoProjectSettingsControlBuilder
import org.rust.cargo.project.settings.controls.builders.CargoProjectSettingsControlBuilderImpl

class CargoProjectSettingsControl(private val myBuilder: CargoProjectSettingsControlBuilder) : AbstractExternalProjectSettingsControl<CargoProjectSettings>(null, myBuilder.getInitialSettings(), myBuilder.getExternalSystemSettingsControlCustomizer()) {

    constructor(initialSettings: CargoProjectSettings) : this(CargoProjectSettingsControlBuilderImpl(initialSettings)) {
    }

    override fun fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {
        myBuilder.createAndFillControls(content, indentLevel)
    }

    @Throws(ConfigurationException::class)
    override fun validate(settings: CargoProjectSettings): Boolean {
        return myBuilder.validate(settings)
    }

    override fun applyExtraSettings(settings: CargoProjectSettings) {
        myBuilder.apply(settings)
    }

    override fun updateInitialExtraSettings() {
        myBuilder.apply(initialSettings)
    }

    override fun isExtraSettingModified(): Boolean {
        return myBuilder.isModified()
    }

    override fun resetExtraSettings(isDefaultModuleCreation: Boolean) {
        myBuilder.reset(project, initialSettings, isDefaultModuleCreation)
    }

    fun update(linkedProjectPath: String?, isDefaultModuleCreation: Boolean) {
        myBuilder.update(linkedProjectPath, initialSettings, isDefaultModuleCreation)
    }

    override fun showUi(show: Boolean) {
        super.showUi(show)
        myBuilder.showUi(show)
    }

    override fun setCurrentProject(project: Project?) {
        super.setCurrentProject(project)
        myBuilder.reset(project, initialSettings, false)
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        myBuilder.disposeUIResources()
    }
}

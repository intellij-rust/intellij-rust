package org.rust.cargo.project.settings.controls.builders

import com.intellij.openapi.externalSystem.service.settings.ExternalSystemSettingsControlCustomizer
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import org.rust.cargo.project.settings.CargoProjectSettings

interface CargoProjectSettingsControlBuilder {
    /**
     * Hides/shows components added by the current control}.
     * @param show  flag which indicates if current control' components should be visible
     */
    fun showUi(show: Boolean)

    /**
     * get initial settings
     * @return
     */
    fun getInitialSettings(): CargoProjectSettings

    /**
     * Add Cargo home components to the panel
     */
    fun addCargoHomeComponents(content: PaintAwarePanel, indentLevel: Int): CargoProjectSettingsControlBuilder

    @Throws(ConfigurationException::class)
    fun validate(settings: CargoProjectSettings): Boolean

    fun apply(settings: CargoProjectSettings)

    /**
     * check if something was changed against initial settings
     * @return
     */
    fun isModified(): Boolean

    fun reset(project: Project?, settings: CargoProjectSettings, isDefaultModuleCreation: Boolean)

    fun createAndFillControls(content: PaintAwarePanel, indentLevel: Int)

    fun update(linkedProjectPath: String?, settings: CargoProjectSettings, isDefaultModuleCreation: Boolean)

    fun getExternalSystemSettingsControlCustomizer(): ExternalSystemSettingsControlCustomizer?

    fun disposeUIResources()
}

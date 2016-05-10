package org.rust.cargo.project.configurable

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.suggestToolchain
import javax.swing.JComponent


class RustProjectConfigurable(
    private val project: Project
) : Configurable {

    private val rustProjectSettings = RustProjectSettingsPanel()

    override fun createComponent(): JComponent = rustProjectSettings.createComponent()

    override fun disposeUIResources() = rustProjectSettings.disposeUIResources()

    override fun reset() {
        val settings = project.rustSettings
        val toolchain = settings.toolchain ?: suggestToolchain()

        rustProjectSettings.data = RustProjectSettingsPanel.Data(
            toolchain,
            settings.autoUpdateEnabled
        )
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        rustProjectSettings.validate()
        val settings = project.rustSettings
        rustProjectSettings.data.applyTo(settings)
    }

    override fun isModified(): Boolean {
        val settings = project.rustSettings
        val data = rustProjectSettings.data
        return data.toolchain?.location != settings.toolchain?.location
            || data.autoUpdateEnabled != settings.autoUpdateEnabled
    }

    override fun getDisplayName(): String = "Rust" // sync me with plugin.xml

    override fun getHelpTopic(): String? = null
}


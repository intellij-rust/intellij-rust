package org.rust.cargo.project.configurable

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.ui.JBColor
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.suggestToolchain
import org.rust.cargo.util.StandardLibraryRoots
import org.rust.cargo.util.cargoProject
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modulesWithCargoProject
import javax.swing.JComponent
import javax.swing.JLabel

class RustProjectConfigurable(
    private val project: Project
) : Configurable, Configurable.NoScroll {

    private lateinit var root: JComponent
    private lateinit var rustProjectSettings: RustProjectSettingsPanel
    private lateinit var stdlibLocation: TextFieldWithBrowseButton
    private lateinit var cargoTomlLocation: JLabel

    override fun createComponent(): JComponent {
        val descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
        stdlibLocation.addBrowseFolderListener(
            "Attach Rust sources",
            "Select the folder with Rust standard library source code",
            project,
            descriptor
        )
        return root
    }

    override fun disposeUIResources() = rustProjectSettings.disposeUIResources()

    override fun reset() {
        val settings = project.rustSettings
        val toolchain = settings.toolchain ?: suggestToolchain()

        rustProjectSettings.data = RustProjectSettingsPanel.Data(
            toolchain,
            settings.autoUpdateEnabled
        )
        val module = rustModule

        if (module == null) {
            cargoTomlLocation.text = "N/A"
            cargoTomlLocation.foreground = JBColor.RED
            stdlibLocation.isEnabled = false
            stdlibLocation.text = "N/A"
        } else {
            cargoTomlLocation.text = module.cargoProjectRoot?.findChild(RustToolchain.CARGO_TOML)?.presentableUrl
            cargoTomlLocation.foreground = JBColor.foreground()
            stdlibLocation.isEnabled = true
            stdlibLocation.text = getCurrentStdlibLocation(module)
        }

    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        rustProjectSettings.validateSettings()
        val settings = project.rustSettings
        rustProjectSettings.data.applyTo(settings)
        val module = rustModule
        val newStdlibLocation = stdlibLocation.text
        if (module != null && newStdlibLocation != getCurrentStdlibLocation(module) && !newStdlibLocation.isNullOrBlank()) {
            val roots = StandardLibraryRoots.fromPath(newStdlibLocation)
                ?: throw ConfigurationException("Invalid standard library location: `$newStdlibLocation`")

            runWriteAction { roots.attachTo(module) }
        }

    }

    override fun isModified(): Boolean {
        val settings = project.rustSettings
        val data = rustProjectSettings.data
        val module = rustModule
        return data.toolchain?.location != settings.toolchain?.location
            || data.autoUpdateEnabled != settings.autoUpdateEnabled
            || module != null && stdlibLocation.text != getCurrentStdlibLocation(module)
    }

    override fun getDisplayName(): String = "Rust" // sync me with plugin.xml

    override fun getHelpTopic(): String? = null

    private fun getCurrentStdlibLocation(module: Module): String? {
        val libRoot = module.cargoProject?.packages?.find { it.name == "std" }?.contentRoot?.parent ?: return null
        // If libRoot is inside a zip file, we want to show the path to the zip itself
        return (JarFileSystem.getInstance().getLocalByEntry(libRoot) ?: libRoot).presentableUrl
    }

    private val rustModule: Module? get() = project.modulesWithCargoProject.firstOrNull()
}


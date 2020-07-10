/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RustProjectSettingsService.MacroExpansionEngine
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.ui.layout
import org.rust.openapiext.CheckboxDelegate
import org.rust.openapiext.ComboBoxDelegate
import org.rust.openapiext.pathAsPath
import java.nio.file.Paths
import javax.swing.JComponent

class RsProjectConfigurable(
    project: Project
) : RsConfigurableBase(project), Configurable.NoScroll {

    private val rustProjectSettings = RustProjectSettingsPanel(
        project.cargoProjects.allProjects.firstOrNull()?.rootDir?.pathAsPath ?: Paths.get(".")
    )

    private val macroExpansionEngineComboBox: ComboBox<MacroExpansionEngine> =
        ComboBox(EnumComboBoxModel(MacroExpansionEngine::class.java)).apply {
            renderer = SimpleListCellRenderer.create("") {
                @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                when (it) {
                    MacroExpansionEngine.DISABLED -> "Disable (select only if you have problems with macro expansion)"
                    MacroExpansionEngine.OLD -> "Use old engine (some features are not supported) "
                    MacroExpansionEngine.NEW -> "Use new engine"
                }
            }
        }
    private var macroExpansionEngine: MacroExpansionEngine by ComboBoxDelegate(macroExpansionEngineComboBox)

    private val doctestInjectionCheckbox: JBCheckBox = JBCheckBox()
    private var doctestInjectionEnabled: Boolean by CheckboxDelegate(doctestInjectionCheckbox)

    override fun getDisplayName(): String = "Rust" // sync me with plugin.xml

    override fun createComponent(): JComponent = layout {
        rustProjectSettings.attachTo(this)
        row("Expand declarative macros:", macroExpansionEngineComboBox, """
            Allow plugin to process declarative macro invocations
            to extract information for name resolution and type inference.
        """)
        row("Inject Rust language into documentation comments:", doctestInjectionCheckbox)
    }

    override fun disposeUIResources() = Disposer.dispose(rustProjectSettings)

    override fun reset() {
        val toolchain = settings.toolchain ?: RustToolchain.suggest()

        rustProjectSettings.data = RustProjectSettingsPanel.Data(
            toolchain = toolchain,
            explicitPathToStdlib = settings.explicitPathToStdlib
        )
        macroExpansionEngine = settings.macroExpansionEngine
        doctestInjectionEnabled = settings.doctestInjectionEnabled
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        rustProjectSettings.validateSettings()

        settings.modify {
            it.toolchain = rustProjectSettings.data.toolchain
            it.explicitPathToStdlib = rustProjectSettings.data.explicitPathToStdlib
            it.macroExpansionEngine = macroExpansionEngine
            it.doctestInjectionEnabled = doctestInjectionEnabled
        }
    }

    override fun isModified(): Boolean {
        val data = rustProjectSettings.data
        return data.toolchain?.location != settings.toolchain?.location
            || data.explicitPathToStdlib != settings.explicitPathToStdlib
            || macroExpansionEngine != settings.macroExpansionEngine
            || doctestInjectionEnabled != settings.doctestInjectionEnabled
    }
}

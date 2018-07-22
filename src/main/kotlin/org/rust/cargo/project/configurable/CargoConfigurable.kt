/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.Label
import org.rust.cargo.project.settings.RustProjectSettingsService.FeaturesSetting
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.util.CargoCommandLineEditor
import org.rust.ide.ui.layout
import org.rust.openapiext.CheckboxDelegate
import org.rust.openapiext.ComboBoxDelegate
import java.awt.Component
import javax.swing.*
import kotlin.reflect.KProperty

class CargoConfigurable(project: Project) : RsConfigurableBase(project) {

    private val autoUpdateEnabledCheckbox: JBCheckBox = JBCheckBox()
    private var autoUpdateEnabled: Boolean by CheckboxDelegate(autoUpdateEnabledCheckbox)

    private val externalLinterComboBox: ComboBox<ExternalLinter> = ComboBox(EnumComboBoxModel(ExternalLinter::class.java))
    private var externalLinter: ExternalLinter by ComboBoxDelegate(externalLinterComboBox)

    private val runExternalLinterOnTheFlyCheckbox: JBCheckBox = JBCheckBox()
    private var runExternalLinterOnTheFly: Boolean by CheckboxDelegate(runExternalLinterOnTheFlyCheckbox)

    private val useOfflineCheckbox: JBCheckBox = JBCheckBox()
    private var useOffline: Boolean by CheckboxDelegate(useOfflineCheckbox)

    private val compileAllTargetsCheckBox = JBCheckBox()
    private var compileAllTargets: Boolean by CheckboxDelegate(compileAllTargetsCheckBox)

    private lateinit var externalLinterArguments: CargoCommandLineEditor

    private val cargoFeaturesComboBox = ComboBox<FeaturesSetting>().apply {
        FeaturesSetting.values().forEach { addItem(it) }
        setRenderer(object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val value2 = when (value as FeaturesSetting) {
                    FeaturesSetting.All -> "All"
                    FeaturesSetting.Default -> "Default"
                    FeaturesSetting.NoDefault -> "No default"
                }
                return super.getListCellRendererComponent(list, value2, index, isSelected, cellHasFocus)
            }
        })
    }
    private var cargoFeatures: FeaturesSetting by FeaturesComboDelegate()

    private val cargoFeaturesAdditionalField = JBTextField()
    private var cargoFeaturesAdditional: List<String> by FeaturesTextFieldDelegate()

    override fun getDisplayName(): String = "Cargo"

    override fun createComponent(): JComponent = layout {
        externalLinterArguments = CargoCommandLineEditor(project, "check ") { null }

        row("Watch Cargo.toml:", autoUpdateEnabledCheckbox, """
            Update project automatically if `Cargo.toml` changes.
        """)
        row("Compile all project targets if possible:", compileAllTargetsCheckBox, """
            Pass `--target-all` option to cargo build/check command.
        """)
        row("Offline mode (nightly only):", useOfflineCheckbox, """
            Pass `-Z offline` option to cargo not to perform network requests.
            Used only for nightly toolchain.
        """)
        block("External Linter") {
            row("External linter:", externalLinterComboBox, """
                External tool to use for code analysis.
            """)
            row("Additional arguments:", externalLinterArguments, """
                Additional arguments to pass to `cargo check` or `cargo clippy` command.
            """)
            row("Run external linter to analyze code on the fly:", runExternalLinterOnTheFlyCheckbox, """
                Enable external linter to add code highlighting based on the used linter result.
                Can be CPU-consuming.
            """)
        }
        cargoFeaturesAdditionalField.toolTipText = "Additional features to activate. Space-separated."
        cargoFeaturesAdditionalField.emptyText.text = "Additional features to activate. Space-separated."

        val p = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(cargoFeaturesComboBox)
            add(Label("+"))
            add(cargoFeaturesAdditionalField)
        }

        row("Cargo metadata features:", p, """
            Cargo features to enable.
            Pass --all-features, --no-default-features and --features to `cargo metadata`.
        """)
    }

    override fun isModified(): Boolean =
        autoUpdateEnabled != settings.autoUpdateEnabled
            || externalLinter != settings.externalLinter
            || runExternalLinterOnTheFly != settings.runExternalLinterOnTheFly
            || compileAllTargets != settings.compileAllTargets
            || cargoFeatures != settings.cargoFeatures
            || cargoFeaturesAdditional != settings.cargoFeaturesAdditional
            || useOffline != settings.useOffline
            || externalLinterArguments.text != settings.externalLinterArguments

    override fun apply() {
        settings.modify {
            it.autoUpdateEnabled = autoUpdateEnabled
            it.externalLinter = externalLinter
            it.runExternalLinterOnTheFly = runExternalLinterOnTheFly
            it.externalLinterArguments = externalLinterArguments.text
            it.compileAllTargets = compileAllTargets
            it.cargoFeatures = cargoFeatures
            it.cargoFeaturesAdditional = cargoFeaturesAdditional
            it.useOffline = useOffline
        }
    }

    override fun reset() {
        autoUpdateEnabled = settings.autoUpdateEnabled
        externalLinter = settings.externalLinter
        runExternalLinterOnTheFly = settings.runExternalLinterOnTheFly
        externalLinterArguments.text = settings.externalLinterArguments
        compileAllTargets = settings.compileAllTargets
        cargoFeatures = settings.cargoFeatures
        cargoFeaturesAdditional = settings.cargoFeaturesAdditional
        useOffline = settings.useOffline
    }

    private inner class FeaturesComboDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): FeaturesSetting {
            return cargoFeaturesComboBox.model.selectedItem as FeaturesSetting
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: FeaturesSetting) {
            cargoFeaturesComboBox.selectedItem = value
        }
    }

    private inner class FeaturesTextFieldDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): List<String> {
            return cargoFeaturesAdditionalField.text.split(' ').filterNot { it.isEmpty() }
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: List<String>) {
            cargoFeaturesAdditionalField.text = value.joinToString(separator = " ")
        }
    }
}

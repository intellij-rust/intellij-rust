/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBCheckBox
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.util.CargoCommandLineEditor
import org.rust.ide.ui.layout
import org.rust.openapiext.CheckboxDelegate
import org.rust.openapiext.ComboBoxDelegate
import javax.swing.JComponent

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

    override fun getDisplayName(): String = "Cargo"

    override fun createComponent(): JComponent = layout {
        externalLinterArguments = CargoCommandLineEditor(project, "check ") { null }

        row("Watch Cargo.toml:", autoUpdateEnabledCheckbox, """
            Update project automatically if `Cargo.toml` changes.
        """)
        row("Compile all project targets if possible:", compileAllTargetsCheckBox, """
            Pass `--target-all` option to cargo build/check command.
        """)
        row("Offline mode:", useOfflineCheckbox, """
            Pass `--offline` option to cargo not to perform network requests.
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
    }

    override fun isModified(): Boolean =
        autoUpdateEnabled != settings.autoUpdateEnabled
            || externalLinter != settings.externalLinter
            || runExternalLinterOnTheFly != settings.runExternalLinterOnTheFly
            || compileAllTargets != settings.compileAllTargets
            || useOffline != settings.useOffline
            || externalLinterArguments.text != settings.externalLinterArguments

    override fun apply() {
        settings.modify {
            it.autoUpdateEnabled = autoUpdateEnabled
            it.externalLinter = externalLinter
            it.runExternalLinterOnTheFly = runExternalLinterOnTheFly
            it.externalLinterArguments = externalLinterArguments.text
            it.compileAllTargets = compileAllTargets
            it.useOffline = useOffline
        }
    }

    override fun reset() {
        autoUpdateEnabled = settings.autoUpdateEnabled
        externalLinter = settings.externalLinter
        runExternalLinterOnTheFly = settings.runExternalLinterOnTheFly
        externalLinterArguments.text = settings.externalLinterArguments
        compileAllTargets = settings.compileAllTargets
        useOffline = settings.useOffline
    }
}

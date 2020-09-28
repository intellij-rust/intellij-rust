/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.util.CargoCommandCompletionProvider
import org.rust.cargo.util.RsCommandLineEditor
import org.rust.openapiext.CheckboxDelegate
import org.rust.openapiext.ComboBoxDelegate
import org.rust.openapiext.row
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

    private lateinit var externalLinterArguments: RsCommandLineEditor

    override fun getDisplayName(): String = "Cargo"

    override fun createComponent(): JComponent = panel {
        externalLinterArguments = RsCommandLineEditor(
            project, CargoCommandCompletionProvider(project.cargoProjects, "check ") { null }
        )

        row("Watch Cargo.toml:", """
            Update project automatically if `Cargo.toml` changes.
        """) { autoUpdateEnabledCheckbox() }
        row("Compile all project targets if possible:", """
            Pass `--target-all` option to cargo build/check command.
        """) { compileAllTargetsCheckBox() }
        row("Offline mode:", """
            Pass `--offline` option to cargo not to perform network requests.
        """) { useOfflineCheckbox() }

        titledRow("External Linter") {
            subRowIndent = 0
            row("External tool:", """
                External tool to use for code analysis.
            """) { externalLinterComboBox(growX, pushX) }
            row("Additional arguments:", """
                Additional arguments to pass to `cargo check` or `cargo clippy` command.
            """) { externalLinterArguments(growX, pushX) }
            row("Run external linter to analyze code on the fly:", """
                Enable external linter to add code highlighting based on the used linter result.
                Can be CPU-consuming.
            """) { runExternalLinterOnTheFlyCheckbox() }
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

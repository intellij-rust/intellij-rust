/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import org.rust.cargo.util.CargoCommandLineEditor
import org.rust.ide.ui.layout
import org.rust.openapiext.CheckboxDelegate
import javax.swing.JComponent

class CargoConfigurable(project: Project) : RsConfigurableBase(project) {

    private val autoUpdateEnabledCheckbox: JBCheckBox = JBCheckBox()
    private var autoUpdateEnabled: Boolean by CheckboxDelegate(autoUpdateEnabledCheckbox)

    private val useCargoCheckForBuildCheckbox: JBCheckBox = JBCheckBox()
    private var useCargoCheckForBuild: Boolean by CheckboxDelegate(useCargoCheckForBuildCheckbox)

    private val useCargoCheckAnnotatorCheckbox: JBCheckBox = JBCheckBox()
    private var useCargoCheckAnnotator: Boolean by CheckboxDelegate(useCargoCheckAnnotatorCheckbox)

    private val useOfflineCheckbox: JBCheckBox = JBCheckBox()
    private var useOffline: Boolean by CheckboxDelegate(useOfflineCheckbox)

    private val compileAllTargetsCheckBox = JBCheckBox()
    private var compileAllTargets: Boolean by CheckboxDelegate(compileAllTargetsCheckBox)

    private lateinit var cargoCheckArguments: CargoCommandLineEditor

    override fun getDisplayName(): String = "Cargo"

    override fun createComponent(): JComponent = layout {
        cargoCheckArguments = CargoCommandLineEditor(project, "check ") { null }

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
        block("Cargo Check") {
            row("Use cargo check to analyze code:", useCargoCheckAnnotatorCheckbox, """
                Enable external annotator to add code highlighting based on `cargo check` result.
                Can be CPU-consuming.
            """)
            row("Use cargo check when build project:", useCargoCheckForBuildCheckbox, """
                Use `cargo check` instead of `cargo build`.
                It should decrease action time because it doesn't generate binaries.
            """)
            row("Additional cargo check arguments:", cargoCheckArguments)
        }
    }

    override fun isModified(): Boolean {
        return autoUpdateEnabled != settings.autoUpdateEnabled
            || useCargoCheckForBuild != settings.useCargoCheckForBuild
            || useCargoCheckAnnotator != settings.useCargoCheckAnnotator
            || compileAllTargets != settings.compileAllTargets
            || useOffline != settings.useOffline
            || cargoCheckArguments.text != settings.cargoCheckArguments
    }

    override fun apply() {
        val currentData = settings.data
        settings.data = currentData.copy(
            autoUpdateEnabled = autoUpdateEnabled,
            useCargoCheckForBuild = useCargoCheckForBuild,
            useCargoCheckAnnotator = useCargoCheckAnnotator,
            cargoCheckArguments = cargoCheckArguments.text,
            compileAllTargets = compileAllTargets,
            useOffline = useOffline
        )
    }

    override fun reset() {
        autoUpdateEnabled = settings.autoUpdateEnabled
        useCargoCheckForBuild = settings.useCargoCheckForBuild
        useCargoCheckAnnotator = settings.useCargoCheckAnnotator
        cargoCheckArguments.text = settings.cargoCheckArguments
        compileAllTargets = settings.compileAllTargets
        useOffline = settings.useOffline
    }
}

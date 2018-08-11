/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import org.rust.ide.ui.layout
import org.rust.openapiext.CheckboxDelegate
import javax.swing.JComponent

class CargoConfigurable(project: Project) : RsConfigurableBase(project) {

    private val useCargoCheckForBuildCheckbox: JBCheckBox = JBCheckBox()
    private var useCargoCheckForBuild: Boolean by CheckboxDelegate(useCargoCheckForBuildCheckbox)

    private val useCargoCheckAnnotatorCheckbox: JBCheckBox = JBCheckBox()
    private var useCargoCheckAnnotator: Boolean by CheckboxDelegate(useCargoCheckAnnotatorCheckbox)

    private val useOfflineForCargoCheckCheckbox: JBCheckBox = JBCheckBox()
    private var useOfflineForCargoCheck: Boolean by CheckboxDelegate(useOfflineForCargoCheckCheckbox)

    private val compileAllTargetsCheckBox = JBCheckBox()
    private var compileAllTargets: Boolean by CheckboxDelegate(compileAllTargetsCheckBox)

    override fun getDisplayName(): String = "Cargo"

    override fun createComponent(): JComponent = layout {
        row("Use cargo check to analyze code:", useCargoCheckAnnotatorCheckbox)
        row("Use cargo check when build project:", useCargoCheckForBuildCheckbox)
        row("Compile all project targets if possible:", compileAllTargetsCheckBox)
        row("Use '-Zoffline' for cargo check (nightly only):", useOfflineForCargoCheckCheckbox)
    }

    override fun isModified(): Boolean {
        return useCargoCheckForBuild != settings.useCargoCheckForBuild
            || useCargoCheckAnnotator != settings.useCargoCheckAnnotator
            || compileAllTargets != settings.compileAllTargets
            || useOfflineForCargoCheck != settings.useOfflineForCargoCheck
    }

    override fun apply() {
        val currentData = settings.data
        settings.data = currentData.copy(
            useCargoCheckForBuild = useCargoCheckForBuild,
            useCargoCheckAnnotator = useCargoCheckAnnotator,
            compileAllTargets = compileAllTargets,
            useOfflineForCargoCheck = useOfflineForCargoCheck
        )
    }

    override fun reset() {
        useCargoCheckForBuild = settings.useCargoCheckForBuild
        useCargoCheckAnnotator = settings.useCargoCheckAnnotator
        compileAllTargets = settings.compileAllTargets
        useOfflineForCargoCheck = settings.useOfflineForCargoCheck
    }
}

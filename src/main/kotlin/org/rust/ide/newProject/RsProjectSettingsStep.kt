/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.Disposer
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.ui.layout.panel
import org.rust.ide.newProject.ui.RsNewProjectPanel
import javax.swing.JPanel

class RsProjectSettingsStep(generator: DirectoryProjectGenerator<ConfigurationData>)
    : ProjectSettingsStepBase<ConfigurationData>(generator, AbstractNewProjectStep.AbstractCallback()) {

    private val newProjectPanel = RsNewProjectPanel(showProjectTypeSelection = true, validate = true).also {
        Disposer.register(this, it)
    }

    override fun createAdvancedSettings(): JPanel = panel {
        newProjectPanel.attachTo(this)
    }

    override fun checkValid(): Boolean {
        return try {
            newProjectPanel.validateSettings()
            true
        } catch (e: ConfigurationException) {
            false
        }
    }
}

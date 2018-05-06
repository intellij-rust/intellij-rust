/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.GeneratorPeerImpl
import org.rust.ide.newProject.ui.RsNewProjectPanel
import org.rust.ide.ui.layout
import javax.swing.JComponent

class RsProjectGeneratorPeer : GeneratorPeerImpl<ConfigurationData>() {

    private val newProjectPanel = RsNewProjectPanel(showProjectTypeCheckbox = true) { checkValid?.run() }
    private var checkValid: Runnable? = null

    override fun getSettings(): ConfigurationData = newProjectPanel.data

    override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
        this.checkValid = checkValid
        return super.getComponent(myLocationField, checkValid)
    }

    override fun getComponent(): JComponent = layout {
        newProjectPanel.attachTo(this)
    }

    override fun validate(): ValidationInfo? = try {
        newProjectPanel.validateSettings()
        null
    } catch (e: ConfigurationException) {
        ValidationInfo(e.message ?: "")
    }
}

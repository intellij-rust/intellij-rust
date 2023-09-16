/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.ui.dsl.builder.panel
import org.rust.ide.newProject.ui.RsNewProjectPanel
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent

class RsProjectGeneratorPeer(cargoProjectDir: Path = Paths.get(".")) : GeneratorPeerImpl<ConfigurationData>() {

    private val newProjectPanel = RsNewProjectPanel(showProjectTypeSelection = true, cargoProjectDir) { checkValid?.run() }
    var checkValid: Runnable? = null

    override fun getSettings(): ConfigurationData = newProjectPanel.data

    override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
        this.checkValid = checkValid
        return super.getComponent(myLocationField, checkValid)
    }

    override fun getComponent(): JComponent = panel {
        newProjectPanel.attachTo(this)
    }

    override fun validate(): ValidationInfo? = try {
        newProjectPanel.validateSettings()
        null
    } catch (e: ConfigurationException) {
        ValidationInfo(e.message ?: "")
    }
}

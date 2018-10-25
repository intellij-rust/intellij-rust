/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBRadioButton
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.ide.newProject.ConfigurationData
import org.rust.ide.ui.RsLayoutBuilder
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.ButtonGroup

class RsNewProjectPanel(
    private val showProjectTypeCheckbox: Boolean,
    updateListener: (() -> Unit)? = null
) : Disposable {

    private val rustProjectSettings = RustProjectSettingsPanel(updateListener = updateListener)
    private val templateButtons = listOf(JBRadioButton("Binary (application)", true), JBRadioButton("Library"))
    private val checkboxListener = ItemListener {
        if (it.stateChange == ItemEvent.SELECTED) updateListener?.invoke()
    }

    init {
        templateButtons.forEach { it.addItemListener(checkboxListener) }
    }

    val data: ConfigurationData get() = ConfigurationData(rustProjectSettings.data, templateButtons[0].isSelected)

    fun attachTo(layout: RsLayoutBuilder) = with(layout) {
        rustProjectSettings.attachTo(this)
        if (showProjectTypeCheckbox) {
            val buttonGroup = ButtonGroup()
            templateButtons.forEachIndexed { index, button ->
                buttonGroup.add(button)
                row(if (index == 0) "Project template:" else "", button)
            }
        }
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        rustProjectSettings.validateSettings()
    }

    override fun dispose() {
        templateButtons.forEach { it.removeItemListener(checkboxListener) }
        Disposer.dispose(rustProjectSettings)
    }
}

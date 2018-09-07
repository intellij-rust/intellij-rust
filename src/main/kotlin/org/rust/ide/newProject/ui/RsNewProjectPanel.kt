/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBCheckBox
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.ide.newProject.ConfigurationData
import org.rust.ide.ui.RsLayoutBuilder
import java.awt.event.ItemListener

class RsNewProjectPanel(
    private val showProjectTypeCheckbox: Boolean,
    updateListener: (() -> Unit)? = null
) : Disposable {
    private val rustProjectSettings: RustProjectSettingsPanel =
        RustProjectSettingsPanel(updateListener = updateListener)

    private val createBinaryCheckbox: JBCheckBox =
        JBCheckBox(null, true)

    private val checkboxListener: ItemListener =
        ItemListener { updateListener?.invoke() }

    val data: ConfigurationData
        get() = ConfigurationData(rustProjectSettings.data, createBinaryCheckbox.isSelected)

    init {
        createBinaryCheckbox.addItemListener(checkboxListener)
    }

    fun attachTo(layout: RsLayoutBuilder) =
        with(layout) {
            rustProjectSettings.attachTo(this)
            if (showProjectTypeCheckbox) {
                row("Use a binary (application) template:", createBinaryCheckbox)
            }
        }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        rustProjectSettings.validateSettings()
    }

    override fun dispose() {
        createBinaryCheckbox.removeItemListener(checkboxListener)
        rustProjectSettings.dispose()
    }
}

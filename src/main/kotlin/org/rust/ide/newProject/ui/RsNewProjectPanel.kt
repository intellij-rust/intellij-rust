/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.LayoutBuilder
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.ide.newProject.ConfigurationData

class RsNewProjectPanel(
    private val showProjectTypeCheckbox: Boolean,
    updateListener: (() -> Unit)? = null
) : Disposable {

    private val rustProjectSettings = RustProjectSettingsPanel(updateListener = updateListener)
    private val createBinaryCheckbox = JBCheckBox(null, true)

    val data: ConfigurationData get() = ConfigurationData(rustProjectSettings.data, createBinaryCheckbox.isSelected)

    fun attachTo(layout: LayoutBuilder) = with(layout) {
        rustProjectSettings.attachTo(this)
        if (showProjectTypeCheckbox) {
            row("Use a binary (application) template:") { createBinaryCheckbox() }
        }
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        rustProjectSettings.validateSettings()
    }

    override fun dispose() {
        rustProjectSettings.dispose()
    }
}

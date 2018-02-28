/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.rust.openapiext.CheckboxDelegate
import javax.swing.JComponent

class RsAutoImportOptions : AutoImportOptionsProvider {

    private val showImportPopupCheckbox: JBCheckBox = JBCheckBox("Show import popup")
    private var showImportPopup: Boolean by CheckboxDelegate(showImportPopupCheckbox)

    override fun createComponent(): JComponent = panel {
        row { showImportPopupCheckbox() }
    }.apply { border = IdeBorderFactory.createTitledBorder("Rust") }

    override fun isModified(): Boolean {
        return showImportPopup != RsCodeInsightSettings.getInstance().showImportPopup
    }

    override fun apply() {
        RsCodeInsightSettings.getInstance().showImportPopup = showImportPopup
    }

    override fun reset() {
        showImportPopup = RsCodeInsightSettings.getInstance().showImportPopup
    }
}

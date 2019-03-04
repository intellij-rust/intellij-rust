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

    private val importOutOfScopeItemsCheckbox: JBCheckBox = JBCheckBox("Import out-of-scope items on completion")
    private var importOutOfScopeItems: Boolean by CheckboxDelegate(importOutOfScopeItemsCheckbox)

    override fun createComponent(): JComponent = panel {
        row { showImportPopupCheckbox() }
        row { importOutOfScopeItemsCheckbox() }
    }.apply { border = IdeBorderFactory.createTitledBorder("Rust") }

    override fun isModified(): Boolean {
        return showImportPopup != RsCodeInsightSettings.getInstance().showImportPopup ||
            importOutOfScopeItems != RsCodeInsightSettings.getInstance().importOutOfScopeItems
    }

    override fun apply() {
        val settings = RsCodeInsightSettings.getInstance()
        settings.showImportPopup = showImportPopup
        settings.importOutOfScopeItems = importOutOfScopeItems
    }

    override fun reset() {
        val settings = RsCodeInsightSettings.getInstance()
        showImportPopup = settings.showImportPopup
        importOutOfScopeItems = settings.importOutOfScopeItems
    }
}

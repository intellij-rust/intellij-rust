/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.application.options.CodeCompletionOptionsCustomSection
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.rust.openapiext.CheckboxDelegate
import javax.swing.JPanel

class RsCodeCompletionConfigurable : CodeCompletionOptionsCustomSection {
    private val suggestOutOfScopeItemsCheckbox: JBCheckBox = JBCheckBox("Suggest out of scope items")
    private var suggestOutOfScopeItems: Boolean by CheckboxDelegate(suggestOutOfScopeItemsCheckbox)

    override fun createComponent(): JPanel = panel {
        row { suggestOutOfScopeItemsCheckbox() }
    }.apply { border = IdeBorderFactory.createTitledBorder("Rust") }

    override fun isModified(): Boolean {
        return suggestOutOfScopeItems != RsCodeInsightSettings.getInstance().suggestOutOfScopeItems
    }

    override fun apply() {
        RsCodeInsightSettings.getInstance().suggestOutOfScopeItems = suggestOutOfScopeItems
    }

    override fun reset() {
        suggestOutOfScopeItems = RsCodeInsightSettings.getInstance().suggestOutOfScopeItems
    }
}

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

class RustfmtConfigurable(project: Project) : RsConfigurableBase(project) {
    private val useRustfmtCheckbox: JBCheckBox = JBCheckBox()
    private var useRustfmt: Boolean by CheckboxDelegate(useRustfmtCheckbox)

    private val runRustfmtOnSaveCheckbox: JBCheckBox = JBCheckBox()
    private var runRustfmtOnSave: Boolean by CheckboxDelegate(runRustfmtOnSaveCheckbox)

    override fun getDisplayName(): String = "Rustfmt"

    override fun createComponent(): JComponent? = layout {
        row("Use rustfmt instead of built-in formatter:", useRustfmtCheckbox)
        row("Run rustfmt on Save:", runRustfmtOnSaveCheckbox)
    }

    override fun isModified(): Boolean =
        settings.useRustfmt != useRustfmt
            || settings.runRustfmtOnSave != runRustfmtOnSave

    override fun apply() {
        settings.modify {
            it.useRustfmt = useRustfmt
            it.runRustfmtOnSave = runRustfmtOnSave
        }
    }

    override fun reset() {
        useRustfmt = settings.useRustfmt
        runRustfmtOnSave = settings.runRustfmtOnSave
    }
}

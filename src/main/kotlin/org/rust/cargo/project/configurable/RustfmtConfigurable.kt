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

    private val useSkipChildrenCheckbox: JBCheckBox = JBCheckBox()
    private var useSkipChildren: Boolean by CheckboxDelegate(useSkipChildrenCheckbox)

    override fun getDisplayName(): String = "Rustfmt"

    override fun createComponent(): JComponent? = layout {
        row("Use rustfmt instead of built-in formatter:", useRustfmtCheckbox)
        row("Run rustfmt on Save:", runRustfmtOnSaveCheckbox)
        row("Don't reformat child modules (nightly only):", useSkipChildrenCheckbox, """
            Pass `--skip-children` option to rustfmt not to reformat child modules.
            Used only for nightly toolchain.
        """)
    }

    override fun isModified(): Boolean =
        settings.useRustfmt != useRustfmt
            || settings.runRustfmtOnSave != runRustfmtOnSave
            || settings.useSkipChildren != useSkipChildren

    override fun apply() {
        settings.modify {
            it.useRustfmt = useRustfmt
            it.runRustfmtOnSave = runRustfmtOnSave
            it.useSkipChildren = useSkipChildren
        }
    }

    override fun reset() {
        useRustfmt = settings.useRustfmt
        runRustfmtOnSave = settings.runRustfmtOnSave
        useSkipChildren = settings.useSkipChildren
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel

class RustfmtConfigurable(project: Project) : RsConfigurableBase(project, "Rustfmt") {
    override fun createPanel(): DialogPanel = panel {
        row { checkBox("Use rustfmt instead of built-in formatter", state::useRustfmt) }
        row { checkBox("Run rustfmt on Save", state::runRustfmtOnSave) }
    }
}

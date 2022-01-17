/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import org.rust.RsBundle

class RustfmtConfigurable(project: Project) : RsConfigurableBase(project, RsBundle.message("settings.rust.rustfmt.name")) {
    override fun createPanel(): DialogPanel = panel {
        row { checkBox(RsBundle.message("settings.rust.rustfmt.builtin.formatter.label"), state::useRustfmt) }
        row { checkBox(RsBundle.message("settings.rust.rustfmt.run.on.save.label"), state::runRustfmtOnSave) }
    }
}

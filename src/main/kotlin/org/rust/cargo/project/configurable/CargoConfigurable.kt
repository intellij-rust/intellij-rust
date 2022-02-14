/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import org.rust.RsBundle

class CargoConfigurable(project: Project) : RsConfigurableBase(project, RsBundle.message("settings.rust.cargo.name")) {
    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(
                RsBundle.message("settings.rust.cargo.show.first.error.label"),
                state::autoShowErrorsInEditor
            )
        }
        row {
            checkBox(
                RsBundle.message("settings.rust.cargo.auto.update.project.label"),
                state::autoUpdateEnabled
            )
        }
        row {
            checkBox(
                RsBundle.message("settings.rust.cargo.compile.all.targets.label"),
                state::compileAllTargets,
                comment = RsBundle.message("settings.rust.cargo.compile.all.targets.comment")
            )
        }
        row {
            checkBox(
                RsBundle.message("settings.rust.cargo.offline.mode.label"),
                state::useOffline,
                comment = RsBundle.message("settings.rust.cargo.offline.mode.comment")
            )
        }
    }
}

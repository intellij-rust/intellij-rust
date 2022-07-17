/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.rust.RsBundle
import org.rust.cargo.project.model.isNewProjectModelImportEnabled

class CargoConfigurable(project: Project) : RsConfigurableBase(project, RsBundle.message("settings.rust.cargo.name")) {
    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(RsBundle.message("settings.rust.cargo.show.first.error.label"))
                .bindSelected(state::autoShowErrorsInEditor)
        }
        // Project model updates is controlled with `Preferences | Build, Execution, Deployment | Build Tools` settings
        // in case of new approach
        if (!isNewProjectModelImportEnabled) {
            row {
                checkBox(RsBundle.message("settings.rust.cargo.auto.update.project.label"))
                    .bindSelected(state::autoUpdateEnabled)
            }
        }
        row {
            checkBox(RsBundle.message("settings.rust.cargo.compile.all.targets.label"))
                .comment(RsBundle.message("settings.rust.cargo.compile.all.targets.comment"))
                .bindSelected(state::compileAllTargets)
        }
        row {
            checkBox(RsBundle.message("settings.rust.cargo.offline.mode.label"),)
                .comment(RsBundle.message("settings.rust.cargo.offline.mode.comment"))
                .bindSelected(state::useOffline)
        }
    }
}

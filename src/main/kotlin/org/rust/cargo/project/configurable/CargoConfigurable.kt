/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel

class CargoConfigurable(project: Project) : RsConfigurableBase(project, "Cargo") {
    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(
                "Automatically show first error in editor after a build failure",
                state::autoShowErrorsInEditor
            )
        }
        row {
            checkBox(
                "Update project automatically if Cargo.toml changes",
                state::autoUpdateEnabled
            )
        }
        row {
            checkBox(
                "Compile all project targets if possible",
                state::compileAllTargets,
                comment = "Pass <b>--target-all</b> option to Сargo <b>build</b>/<b>check</b> command"
            )
        }
        row {
            checkBox(
                "Offline mode",
                state::useOffline,
                comment = "Pass <b>--offline</b> option to Cargo not to perform network requests"
            )
        }
    }
}

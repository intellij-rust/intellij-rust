/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.getRuntimeType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.titledRow
import org.rust.RsBundle

class RsLanguageRuntimeConfigurable(val config: RsLanguageRuntimeConfiguration) :
    BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

    override fun createPanel(): DialogPanel = panel {
        titledRow()

        row(RsBundle.message("run.target.rustc.executable.path.label")) {
            textField(config::rustcPath)
        }
        row(RsBundle.message("run.target.rustc.executable.version.label")) {
            textField(config::rustcVersion).enabled(false)
        }

        row(RsBundle.message("run.target.cargo.executable.path.label")) {
            textField(config::cargoPath)
        }
        row(RsBundle.message("run.target.cargo.executable.version.label")) {
            textField(config::cargoVersion).enabled(false)
        }

        row(RsBundle.message("run.target.build.arguments.label")) {
            textField(config::localBuildArgs)
                .comment(RsBundle.message("run.target.build.arguments.comment"))
                .apply { component.emptyText.text = "e.g. --target=x86_64-unknown-linux-gnu" }
        }
    }
}

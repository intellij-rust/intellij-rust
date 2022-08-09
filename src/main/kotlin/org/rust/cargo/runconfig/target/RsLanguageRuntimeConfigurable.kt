/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.getRuntimeType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.rust.RsBundle

class RsLanguageRuntimeConfigurable(val config: RsLanguageRuntimeConfiguration) :
    BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

    override fun createPanel(): DialogPanel = panel {
        row(RsBundle.message("run.target.rustc.executable.path.label")) {
            fullWidthTextField()
                .bindText(config::rustcPath)
        }
        row(RsBundle.message("run.target.rustc.executable.version.label")) {
            fullWidthTextField()
                .enabled(false)
                .bindText(config::rustcVersion)
        }
        row(RsBundle.message("run.target.cargo.executable.path.label")) {
            fullWidthTextField()
                .bindText(config::cargoPath)
        }
        row(RsBundle.message("run.target.cargo.executable.version.label")) {
            fullWidthTextField()
                .enabled(false)
                .bindText(config::cargoVersion)
        }
        row(RsBundle.message("run.target.build.arguments.label")) {
            fullWidthTextField()
                .comment(RsBundle.message("run.target.build.arguments.comment"))
                .apply { component.emptyText.text = "e.g. --target=x86_64-unknown-linux-gnu" }
                .bindText(config::localBuildArgs)
        }
    }

    private fun Row.fullWidthTextField(): Cell<JBTextField> = textField().horizontalAlign(HorizontalAlign.FILL)
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.ui.GotItTooltip
import org.rust.RsBundle
import org.rust.cargo.project.configurable.RsExternalLinterConfigurable
import org.rust.cargo.project.settings.externalLinterSettings
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.openapiext.showSettingsDialog
import javax.swing.JComponent

@Service
class RsExternalLinterTooltipService(private val project: Project) : Disposable {
    private val linter: ExternalLinter get() = project.externalLinterSettings.tool
    private val turnedOn: Boolean get() = project.externalLinterSettings.runOnTheFly

    fun showTooltip(component: JComponent) {
        val tooltip = createTooltip(turnedOn)
        tooltip.show(component, GotItTooltip.TOP_MIDDLE)
    }

    override fun dispose() {}

    private fun createTooltip(turnedOn: Boolean): GotItTooltip {
        val headerText = RsBundle.message("0.on.the.fly.analysis.is.turned.1.choice.0.on.1.off", linter.title, if (turnedOn) 0 else 1)
        val text = RsBundle.message("external.linter.tooltip", linter.title)
        return GotItTooltip("rust.linter.on-the-fly.got.it", text, this)
            .withHeader(headerText)
            .withLink(RsBundle.message("configure")) {
                project.showSettingsDialog<RsExternalLinterConfigurable>()
            }
    }
}

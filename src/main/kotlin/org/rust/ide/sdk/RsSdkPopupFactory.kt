/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.util.text.trimMiddle
import org.rust.cargo.project.configurable.RsConfigurableToolchainList
import org.rust.cargo.project.settings.rustSettings
import org.rust.ide.sdk.RsSdkRenderingUtils.icon
import org.rust.ide.sdk.RsSdkRenderingUtils.name
import org.rust.ide.sdk.RsSdkRenderingUtils.path
import org.rust.ide.sdk.add.RsAddSdkDialog

class RsSdkPopupFactory(val project: Project) {

    fun createPopup(context: DataContext): ListPopup? {
        val toolchainList = RsConfigurableToolchainList.getInstance(project)
        val model = toolchainList.model

        val sdks = toolchainList.allRustSdks.filterNot { RsSdkUtils.isInvalid(it) }
        val actions = sdks.mapNotNull { model.findSdk(it) }.map { SwitchToSdkAction(it) }
        val group = DefaultActionGroup(actions)
        if (sdks.isNotEmpty()) group.addSeparator()
        group.add(ShowSettingsAction())
        group.add(AddToolchainAction())

        val currentSdk = project.rustSettings.sdk
        return JBPopupFactory.getInstance().createActionGroupPopup(
            "Rust Toolchain",
            group,
            context,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false,
            null,
            -1,
            { it is SwitchToSdkAction && it.sdk == currentSdk },
            null
        ).apply { setHandleAutoSelectionBeforeShow(true) }
    }

    private fun switchToSdk(sdk: Sdk) {
        (sdk.sdkType as RsSdkType).setupSdkPaths(sdk)
        project.rustSettings.modify { it.sdk = sdk }
    }

    private inner class SwitchToSdkAction(val sdk: Sdk) : DumbAwareAction() {

        init {
            val presentation = templatePresentation
            presentation.setText(shortenNameInPopup(sdk, 100), false)
            presentation.description = "Switch to ${descriptionInPopup(sdk)}"
            presentation.icon = icon(sdk)
        }

        override fun actionPerformed(e: AnActionEvent) = switchToSdk(sdk)
    }

    private inner class ShowSettingsAction : DumbAwareAction("Show Settings...") {
        override fun actionPerformed(e: AnActionEvent) {
            project.rustSettings.configureToolchain()
        }
    }

    private inner class AddToolchainAction : DumbAwareAction("Add Toolchain...") {
        override fun actionPerformed(e: AnActionEvent) {
            val model = RsConfigurableToolchainList.getInstance(project).model
            RsAddSdkDialog.show(project, model.sdks.asList()) {
                if (it != null && model.findSdk(it.name) == null) {
                    model.addSdk(it)
                    model.apply()
                    switchToSdk(it)
                }
            }
        }
    }

    companion object {

        private fun nameInPopup(sdk: Sdk): String {
            val (primary, secondary, _) = name(sdk)
            return if (secondary == null) primary else "$primary [$secondary]"
        }

        fun shortenNameInPopup(sdk: Sdk, maxLength: Int) = nameInPopup(sdk).trimMiddle(maxLength)

        fun descriptionInPopup(sdk: Sdk) = "${nameInPopup(sdk)} [${path(sdk)}]".trimMiddle(150)
    }
}

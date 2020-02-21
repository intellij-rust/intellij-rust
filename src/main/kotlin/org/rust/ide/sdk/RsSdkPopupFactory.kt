/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Condition
import com.intellij.util.text.trimMiddle
import com.intellij.util.ui.SwingHelper
import org.rust.cargo.project.configurable.RsConfigurableToolchainList
import org.rust.ide.sdk.add.RsAddSdkDialog

class RsSdkPopupFactory(val project: Project, val module: Module) {

    fun createPopup(context: DataContext): ListPopup? {
        val group = DefaultActionGroup()

        val toolchainList = RsConfigurableToolchainList.getInstance(project)
        val model = toolchainList.model
        val moduleSdks = toolchainList.allRustSdks
            .filter { !it.isAssociatedWithAnotherModule(module) && !RsSdkUtils.isInvalid(it) }
            .mapNotNull { model.findSdk(it) }
            .map { SwitchToSdkAction(it) }

        moduleSdks.forEach { group.add(it) }
        if (moduleSdks.isNotEmpty()) group.addSeparator()
        group.add(ToolchainSettingsAction())
        group.add(AddToolchainAction())

        val currentSdk = module.rustSdk
        return JBPopupFactory.getInstance().createActionGroupPopup(
            "Project Toolchain",
            group,
            context,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false,
            null,
            -1,
            Condition { it is SwitchToSdkAction && it.sdk == currentSdk },
            null
        ).apply { setHandleAutoSelectionBeforeShow(true) }
    }

    private fun shortenNameInPopup(sdk: Sdk): String = nameInPopup(sdk).trimMiddle(100)

    private fun switchToSdk(sdk: Sdk) {
        (sdk.sdkType as RsSdkType).setupSdkPaths(sdk)
        project.rustSdk = sdk
        module.rustSdk = sdk
    }

    private inner class SwitchToSdkAction(val sdk: Sdk) : DumbAwareAction() {

        init {
            val presentation = templatePresentation
            presentation.setText(shortenNameInPopup(sdk), false)
            presentation.description = "Switch to ${descriptionInPopup(sdk)}]"
            presentation.icon = icon(sdk)
        }

        override fun actionPerformed(e: AnActionEvent) = switchToSdk(sdk)
    }

    private inner class ToolchainSettingsAction : DumbAwareAction("Toolchain Settings...") {
        override fun actionPerformed(e: AnActionEvent) =
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Toolchain")
    }

    private inner class AddToolchainAction : DumbAwareAction("Add Toolchain...") {

        override fun actionPerformed(e: AnActionEvent) {
            val model = RsConfigurableToolchainList.getInstance(project).model
            RsAddSdkDialog.show(project, module, model.sdks.asList()) {
                if (it != null && model.findSdk(it.name) == null) {
                    model.addSdk(it)
                    model.apply()
                    switchToSdk(it)
                }
            }
        }
    }

    companion object {

        fun nameInPopup(sdk: Sdk): String {
            val sdkName = name(sdk)
            return if (sdkName.secondary == null) sdkName.primary else "${sdkName.primary} [${sdkName.secondary}]"
        }

        fun descriptionInPopup(sdk: Sdk): String = "${nameInPopup(sdk)} [${path(sdk)}]".trimMiddle(150)

        fun createAndShow(project: Project, module: Module) {
            DataManager.getInstance()
                .dataContextFromFocusAsync
                .onSuccess {
                    val popup = RsSdkPopupFactory(project, module).createPopup(it) ?: return@onSuccess

                    val component = SwingHelper.getComponentFromRecentMouseEvent()
                    if (component != null) {
                        popup.showUnderneathOf(component)
                    } else {
                        popup.showInBestPositionFor(it)
                    }
                }
        }
    }
}

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.modulesWithCargoProject
import org.rust.ide.utils.isNullOrEmpty

class RsCargoActionGroup : ActionGroup() {
    val actions : Array<AnAction>

    init {
        templatePresentation.text = "_Cargo"
        templatePresentation.description = "Run cargo command"
        templatePresentation.icon = CargoIcons.ICON
        templatePresentation.isEnabledAndVisible = true
        isPopup = true
        actions = arrayOf(RsCargoAction("clean"), RsCargoAction("check"), RsCargoAction("update"))
    }

    override fun update(e: AnActionEvent) {
        if (e.project?.toolchain == null || e.project?.modulesWithCargoProject.isNullOrEmpty()) {
            e.presentation.isEnabled = false
        }
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return actions
    }
}

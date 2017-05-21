package org.rust.cargo.runconfig.command

import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.runconfig.ui.RunCargoCommandDialog

class RunCargoCommandAction : RunCargoCommandActionBase(CargoIcons.ICON) {

    override fun actionPerformed(e: AnActionEvent) {
        val module = getAppropriateModule(e) ?: return
        val dialog = RunCargoCommandDialog(module.project)

        if (!dialog.showAndGet()) return

        runCommand(module, dialog.getCargoCommandLine())
    }

}

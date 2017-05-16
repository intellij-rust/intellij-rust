package org.rust.cargo.runconfig.command

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.PathUtil
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustChannel

class RunClippyAction : RunCargoCommandActionBase(CargoIcons.CLIPPY) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolchain = project.toolchain ?: return
        val module = getAppropriateModule(e) ?: return
        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)
        val channel = if (toolchain.isRustupAvailable) RustChannel.NIGHTLY else RustChannel.DEFAULT

        runCommand(module, toolchain.cargo(moduleDirectory).clippyCommandLine(channel))
    }

}

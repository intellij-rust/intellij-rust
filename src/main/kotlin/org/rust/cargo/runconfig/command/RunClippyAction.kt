/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustChannel
import java.nio.file.Paths

class RunClippyAction : RunCargoCommandActionBase(CargoIcons.CLIPPY) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolchain = project.toolchain ?: return
        val module = getAppropriateModule(e) ?: return
        val moduleDirectory = Paths.get(module.moduleFilePath).parent!!
        val channel = if (toolchain.isRustupAvailable) RustChannel.NIGHTLY else RustChannel.DEFAULT

        runCommand(module, toolchain.cargo(moduleDirectory).clippyCommandLine(channel))
    }

}

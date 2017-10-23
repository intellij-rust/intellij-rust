/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustChannel

class RunClippyAction : RunCargoCommandActionBase(CargoIcons.CLIPPY) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolchain = project.toolchain ?: return
        val cargoProject = getAppropriateCargoProject(e) ?: return
        val channel = if (toolchain.isRustupAvailable) RustChannel.NIGHTLY else RustChannel.DEFAULT
        runCommand(project, CargoCommandLine.forProject(cargoProject, "clippy", channel = channel))
    }
}

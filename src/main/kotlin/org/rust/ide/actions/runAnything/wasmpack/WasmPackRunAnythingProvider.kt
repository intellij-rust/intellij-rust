/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything.wasmpack

import com.intellij.ide.actions.runAnything.items.RunAnythingItem
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.getAppropriateCargoProject
import org.rust.cargo.runconfig.wasmpack.util.WasmPackCommandCompletionProvider
import org.rust.cargo.toolchain.WasmPackCommandLine
import org.rust.cargo.util.RsCommandCompletionProvider
import org.rust.ide.actions.runAnything.RsRunAnythingProvider
import org.rust.ide.icons.RsIcons
import java.nio.file.Path
import javax.swing.Icon

class WasmPackRunAnythingProvider : RsRunAnythingProvider() {

    override fun getMainListItem(dataContext: DataContext, value: String): RunAnythingItem =
        RunAnythingWasmPackItem(getCommand(value), getIcon(value))

    override fun run(command: String, params: List<String>, workingDirectory: Path, cargoProject: CargoProject) {
        WasmPackCommandLine(command, workingDirectory, params).run(cargoProject)
    }

    override fun getCompletionProvider(project: Project, dataContext: DataContext): RsCommandCompletionProvider =
        WasmPackCommandCompletionProvider(project.cargoProjects) {
            getAppropriateCargoProject(dataContext)?.workspace
        }

    override fun getCommand(value: String): String = value

    override fun getIcon(value: String): Icon = RsIcons.WASM_PACK

    override fun getCompletionGroupTitle(): String = "wasm-pack commands"

    override fun getHelpGroupTitle(): String = "wasm-pack"

    override fun getHelpCommandPlaceholder(): String = "wasm-pack <subcommand> <args...>"

    override fun getHelpCommand(): String = HELP_COMMAND

    override fun getHelpIcon(): Icon = RsIcons.WASM_PACK

    override fun getHelpDescription(): String = "Runs wasm-pack command"

    companion object {
        const val HELP_COMMAND = "wasm-pack"
    }
}

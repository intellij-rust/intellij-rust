/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything.cargo

import com.intellij.ide.actions.runAnything.items.RunAnythingItem
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.getAppropriateCargoProject
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.CargoCommandCompletionProvider
import org.rust.cargo.util.RsCommandCompletionProvider
import org.rust.ide.actions.runAnything.RsRunAnythingProvider
import java.nio.file.Path
import javax.swing.Icon

class CargoRunAnythingProvider : RsRunAnythingProvider() {

    override fun getMainListItem(dataContext: DataContext, value: String): RunAnythingItem =
        RunAnythingCargoItem(getCommand(value), getIcon(value))

    override fun run(command: String, params: List<String>, workingDirectory: Path, cargoProject: CargoProject) {
        CargoCommandLine(command, workingDirectory, params).run(cargoProject)
    }

    override fun getCompletionProvider(project: Project, dataContext: DataContext): RsCommandCompletionProvider =
        CargoCommandCompletionProvider(project.cargoProjects) {
            getAppropriateCargoProject(dataContext)?.workspace
        }

    override fun getCommand(value: String): String = value

    override fun getIcon(value: String): Icon = CargoIcons.ICON

    override fun getCompletionGroupTitle(): String = "Cargo commands"

    override fun getHelpGroupTitle(): String = "Cargo"

    override fun getHelpCommandPlaceholder(): String = "cargo <subcommand> <args...>"

    override fun getHelpCommand(): String = HELP_COMMAND

    override fun getHelpIcon(): Icon = CargoIcons.ICON

    override fun getHelpDescription(): String = "Runs Cargo command"

    companion object {
        const val HELP_COMMAND = "cargo"
    }
}

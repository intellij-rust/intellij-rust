/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase
import com.intellij.ide.actions.runAnything.items.RunAnythingItem
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.text.StringUtil.trimStart
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.getAppropriateCargoProject
import org.rust.cargo.runconfig.hasCargoProject
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.run
import org.rust.cargo.util.CargoCommandCompletionProvider
import javax.swing.Icon

class CargoRunAnythingProvider : RunAnythingProviderBase<String>() {

    override fun getMainListItem(dataContext: DataContext, value: String): RunAnythingItem =
        RunAnythingCargoItem(getCommand(value), getIcon(value))

    override fun findMatchingValue(dataContext: DataContext, pattern: String): String? =
        if (pattern.startsWith(helpCommand)) getCommand(pattern) else null

    override fun getValues(dataContext: DataContext, pattern: String): Collection<String> {
        if (!pattern.startsWith(helpCommand)) return emptyList()
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return emptyList()
        if (!project.hasCargoProject) return emptyList()
        val completionProvider = CargoCommandCompletionProvider(project.cargoProjects) {
            getAppropriateCargoProject(dataContext)?.workspace
        }
        val context = trimStart(pattern, helpCommand).substringBeforeLast(' ')
        val prefix = pattern.substringBeforeLast(' ')
        return completionProvider.complete(context).map { "$prefix ${it.lookupString}" }
    }

    override fun execute(dataContext: DataContext, value: String) {
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
        if (!project.hasCargoProject) return
        val cargoProject = getAppropriateCargoProject(dataContext) ?: return
        val params = ParametersListUtil.parse(trimStart(value, helpCommand))
        val commandLine = CargoCommandLine.forProject(
            cargoProject,
            params.firstOrNull() ?: "--help",
            params.drop(1)
        )
        commandLine.run(cargoProject)
    }

    override fun getCommand(value: String): String = value

    override fun getIcon(value: String): Icon = CargoIcons.ICON

    override fun getCompletionGroupTitle(): String = "Cargo commands"

    override fun getHelpCommandPlaceholder(): String = "cargo <subcommand> <args...>"

    override fun getHelpCommand() = "cargo"

    override fun getHelpIcon(): Icon = CargoIcons.ICON

    override fun getHelpDescription(): String = "Runs Cargo command"
}

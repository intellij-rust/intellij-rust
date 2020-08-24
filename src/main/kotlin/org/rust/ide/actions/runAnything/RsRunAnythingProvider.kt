/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase
import com.intellij.ide.actions.runAnything.items.RunAnythingItem
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.trimStart
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.runconfig.getAppropriateCargoProject
import org.rust.cargo.runconfig.hasCargoProject
import org.rust.cargo.util.RsCommandCompletionProvider
import org.rust.stdext.toPath
import java.nio.file.Path

abstract class RsRunAnythingProvider : RunAnythingProviderBase<String>() {

    abstract override fun getMainListItem(dataContext: DataContext, value: String): RunAnythingItem

    abstract fun run(command: String, params: List<String>, workingDirectory: Path, cargoProject: CargoProject)

    abstract fun getCompletionProvider(project: Project, dataContext: DataContext) : RsCommandCompletionProvider

    override fun findMatchingValue(dataContext: DataContext, pattern: String): String? =
        if (pattern.startsWith(helpCommand)) getCommand(pattern) else null

    override fun getValues(dataContext: DataContext, pattern: String): Collection<String> {
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return emptyList()
        if (!project.hasCargoProject) return emptyList()
        val completionProvider = getCompletionProvider(project, dataContext)

        return when {
            pattern.startsWith(helpCommand) -> {
                val context = trimStart(pattern, helpCommand).substringBeforeLast(' ')
                val prefix = pattern.substringBeforeLast(' ')
                completionProvider.complete(context).map { "$prefix ${it.lookupString}" }
            }
            pattern.isNotBlank() && helpCommand.startsWith(pattern) ->
                completionProvider.complete("").map { "$helpCommand ${it.lookupString}" }
            else -> emptyList()
        }
    }

    override fun execute(dataContext: DataContext, value: String) {
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
        if (!project.hasCargoProject) return
        val cargoProject = getAppropriateCargoProject(dataContext) ?: return
        val params = ParametersListUtil.parse(trimStart(value, helpCommand))
        val path = project.basePath?.toPath() ?: return
        run(params.firstOrNull() ?: "--help", params.drop(1), path, cargoProject)
    }

    abstract override fun getHelpCommand(): String
}


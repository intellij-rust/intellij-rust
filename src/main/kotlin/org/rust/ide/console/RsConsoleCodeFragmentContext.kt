/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.resolve.TYPES_N_VALUES_N_MACROS
import org.rust.lang.core.resolve.createProcessor
import org.rust.lang.core.resolve.findPrelude
import org.rust.lang.core.resolve.processNestedScopesUpwards
import org.rust.openapiext.toPsiFile

class RsConsoleCodeFragmentContext(codeFragment: RsReplCodeFragment?) {

    // concurrent set is needed because accessed both in `addItemsNamesFromPrelude` and `addToContext`
    private val itemsNames: MutableSet<String> = ContainerUtil.newConcurrentSet()

    @Volatile
    private var hasAddedNamesFromPrelude = false

    private val commands: MutableList<String> = mutableListOf()

    init {
        if (codeFragment != null) {
            DumbService.getInstance(codeFragment.project).runWhenSmart {
                addItemsNamesFromPrelude(codeFragment)
            }
        }
    }

    // see org.rust.ide.console.RsConsoleCompletionTest.`test redefine struct from prelude`
    private fun addItemsNamesFromPrelude(codeFragment: RsReplCodeFragment) {
        val prelude = findPrelude(codeFragment) ?: return

        val preludeItemsNames = mutableListOf<String>()
        val processor = createProcessor {
            preludeItemsNames += it.name
            false
        }
        processNestedScopesUpwards(prelude, TYPES_N_VALUES_N_MACROS, processor)
        itemsNames += preludeItemsNames
        hasAddedNamesFromPrelude = true
    }

    fun addToContext(lastCommandContext: RsConsoleOneCommandContext) {
        if (commands.isEmpty()
            || lastCommandContext.itemsNames.any(itemsNames::contains)
            || lastCommandContext.containsUseDirective
            || !hasAddedNamesFromPrelude
        ) {
            commands.add(lastCommandContext.command)
        } else {
            commands[commands.size - 1] = commands.last() + "\n" + lastCommandContext.command
        }
        itemsNames += lastCommandContext.itemsNames
    }

    fun updateContextAsync(project: Project, codeFragment: RsReplCodeFragment) {
        DumbService.getInstance(project).smartInvokeLater {
            runWriteAction {
                updateContext(project, codeFragment)
            }
        }
    }

    fun updateContext(project: Project, codeFragment: RsReplCodeFragment) {
        codeFragment.context = createContext(project, codeFragment.crateRoot as RsFile?, commands)
    }

    fun getAllCommandsText(): String {
        return commands.joinToString("\n")
    }

    fun clearAllCommands() {
        commands.clear()
    }

    companion object {
        fun createContext(project: Project, originalCrateRoot: RsFile?, commands: List<String> = listOf("")): RsBlock {
            // command may contain functions/structs with same names as in previous commands
            // therefore we should put such commands in separate scope
            // we do this like so:
            // ```
            // command1;
            // {
            //     command2;
            //     {
            //         command3;
            //     }
            // }
            // And `RsBlock` surrounding `command3` will become context of codeFragment
            // ```
            val functionBody = commands.joinToString("\n{\n") + "\n}".repeat(commands.size - 1)
            val rsFile = RsPsiFactory(project).createFile("fn main() { $functionBody }")

            val crateRoot = originalCrateRoot ?: findAnyCrateRoot(project)
            crateRoot?.let { rsFile.originalFile = crateRoot }

            val functionBlock = rsFile.childOfType<RsFunction>()!!.block!!
            val blocks = generateSequence(functionBlock) {
                (it.expr as? RsBlockExpr)?.block
            }
            return blocks.drop(commands.size - 1).first()
        }

        private fun findAnyCrateRoot(project: Project): RsFile? {
            val cargoProject = project.cargoProjects.allProjects.first()
            val crateRoot = cargoProject.workspace?.packages?.firstOrNull()?.targets?.firstOrNull()?.crateRoot
            return crateRoot?.toPsiFile(project)?.rustFile
        }
    }
}

class RsConsoleOneCommandContext(codeFragment: RsReplCodeFragment) {
    val command: String
    val itemsNames: Set<String> = codeFragment.namedElementsUnique.keys
    val containsUseDirective: Boolean

    init {
        val elements = codeFragment.expandedStmtsAndTailExpr.first
        command = elements.joinToString("\n") { it.text }
        containsUseDirective = elements.any { it is RsUseItem }
    }
}

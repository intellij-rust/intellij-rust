/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rust.openapiext.toPsiFile

class RsConsoleCodeFragmentContext {

    private val topLevelElements: MutableMap<String, String> = mutableMapOf()
    private val allCommandsText: StringBuilder = StringBuilder()

    fun addToContext(lastCommandContext: RsConsoleOneCommandContext) {
        topLevelElements.putAll(lastCommandContext.topLevelElements)
        allCommandsText.append(lastCommandContext.statementsText)
    }

    fun updateContext(project: Project, codeFragment: RsReplCodeFragment) {
        val allCommandsText = getAllCommandsText()

        runInEdt {
            runWriteAction {
                codeFragment.context = createContext(project, codeFragment.crateRoot as RsFile?, allCommandsText)
            }
        }
    }

    private fun getAllCommandsText(): String {
        val topLevelElementsText = topLevelElements.values.joinToString("\n")
        return topLevelElementsText + "\n" + allCommandsText
    }

    companion object {
        fun createContext(project: Project, originalCrateRoot: RsFile?, allCommandsText: String = ""): RsBlock {
            val rsFile = RsPsiFactory(project).createFile("fn main() { $allCommandsText }")

            val crateRoot = originalCrateRoot ?: findAnyCrateRoot(project)
            crateRoot?.let { rsFile.originalFile = crateRoot }

            return rsFile.descendantOfTypeStrict()!!
        }

        private fun findAnyCrateRoot(project: Project): RsFile? {
            val cargoProject = project.cargoProjects.allProjects.first()
            val crateRoot = cargoProject.workspace?.packages?.firstOrNull()?.targets?.firstOrNull()?.crateRoot
            return crateRoot?.toPsiFile(project)?.rustFile
        }
    }
}

class RsConsoleOneCommandContext(codeFragment: RsReplCodeFragment) {
    val topLevelElements: MutableMap<String, String> = mutableMapOf()
    val statementsText: String

    init {
        for (namedElement in codeFragment.namedElements) {
            val name = namedElement.name ?: continue
            topLevelElements[name] = namedElement.text
        }

        statementsText = codeFragment.stmts.joinToString("\n") { it.text } + "\n"
    }
}

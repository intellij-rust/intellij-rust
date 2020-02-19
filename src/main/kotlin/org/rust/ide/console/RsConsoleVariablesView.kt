/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import org.rust.ide.structure.RsStructureViewModel
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsReplCodeFragment
import org.rust.openapiext.document

class RsConsoleVariablesView(project: Project, private val codeFragmentContext: RsConsoleCodeFragmentContext) :
    SimpleToolWindowPanel(true, true), Disposable {

    private val variablesFile: RsReplCodeFragment
    private val treeStructure: SmartTreeStructure
    private val structureTreeModel: StructureTreeModel<SmartTreeStructure>

    init {
        val allCommands = codeFragmentContext.getAllCommandsText()
        variablesFile = PsiFileFactory.getInstance(project)
            .createFileFromText(RsConsoleView.VIRTUAL_FILE_NAME, RsLanguage, allCommands) as RsReplCodeFragment
        val structureViewModel = RsStructureViewModel(null, variablesFile)
        treeStructure = SmartTreeStructure(project, structureViewModel)

        structureTreeModel = StructureTreeModel(treeStructure, this)
        val asyncTreeModel = AsyncTreeModel(structureTreeModel, this)

        val tree = Tree(asyncTreeModel)
        tree.isRootVisible = false
        tree.emptyText.text = EMPTY_TEXT

        setContent(ScrollPaneFactory.createScrollPane(tree))
    }

    fun rebuild() {
        runInEdt {
            runWriteAction {
                val allCommands = codeFragmentContext.getAllCommandsText()
                variablesFile.virtualFile.document?.setText(allCommands)
            }

            structureTreeModel.invoker.invokeLater {
                treeStructure.rebuildTree()
                structureTreeModel.invalidate()
            }
        }
    }

    override fun dispose() {}

    companion object {
        private const val EMPTY_TEXT: String = "No variables yet"
    }
}

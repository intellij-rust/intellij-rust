/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree

class RsConsoleVariablesView(project: Project, structureViewModel: StructureViewModel) :
    SimpleToolWindowPanel(true, true), Disposable {

    private val treeStructure: SmartTreeStructure = SmartTreeStructure(project, structureViewModel)
    private val structureTreeModel: StructureTreeModel<SmartTreeStructure>

    init {
        structureTreeModel = StructureTreeModel(treeStructure, this)
        val asyncTreeModel = AsyncTreeModel(structureTreeModel, this)
        asyncTreeModel.setRootImmediately(structureTreeModel.rootImmediately)

        val tree = Tree(asyncTreeModel)
        tree.isRootVisible = false
        tree.emptyText.text = EMPTY_TEXT

        setContent(ScrollPaneFactory.createScrollPane(tree))
    }

    fun rebuild() {
        structureTreeModel.invoker.runOrInvokeLater {
            treeStructure.rebuildTree()
            structureTreeModel.invalidate()
        }
    }

    override fun dispose() {}

    companion object {
        private const val EMPTY_TEXT: String = "No variables yet"
    }
}

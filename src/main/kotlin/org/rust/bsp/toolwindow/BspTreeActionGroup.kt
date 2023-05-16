package org.rust.bsp.toolwindow

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.bsp.toolwindow.actions.BuildAction
import javax.swing.tree.DefaultMutableTreeNode

class BspTreeActionGroup(
    val target: BspProjectsTree
) : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val node = target.selectionModel.selectionPath.lastPathComponent as? DefaultMutableTreeNode ?: return arrayOf()
        val selected = (node.userObject as? BspProjectTreeStructure.BspSimpleNode.Target) ?: return arrayOf()
        return listOf(BuildAction(selected.name)).toTypedArray()
    }
}

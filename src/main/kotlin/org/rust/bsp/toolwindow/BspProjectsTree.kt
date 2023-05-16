package org.rust.bsp.toolwindow

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.ui.PopupHandler
import com.intellij.ui.treeStructure.SimpleTree
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class BspProjectsTree : SimpleTree() {

    init {
        isRootVisible = false
        showsRootHandles = true
        emptyText.text = "There are no Rust projects to display."
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        setToggleClickCount(3)
        PopupHandler.installPopupMenu(this, BspTreeActionGroup(this), ActionPlaces.MOUSE_SHORTCUT)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount < 2 || !SwingUtilities.isLeftMouseButton(e)) return
                val tree = e.source as? BspProjectsTree ?: return
                if (tree.selectionModel.isSelectionEmpty) return
                val node = tree.selectionModel.selectionPath.lastPathComponent as? DefaultMutableTreeNode ?: return
                val target = (node.userObject as? BspProjectTreeStructure.BspSimpleNode.Target) ?: return
                val root = (tree.treeModel.root as? DefaultMutableTreeNode) ?: return
                val treeRoot = (root.userObject as? BspProjectTreeStructure.BspSimpleNode.Root) ?: return
                target.click()
                tree.clearSelection()
                treeRoot.checkStatus()
                tree.repaint()
            }
        })

    }

}


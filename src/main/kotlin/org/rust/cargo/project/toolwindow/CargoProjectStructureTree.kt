/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.toolwindow

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.SimpleTree
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class CargoProjectStructureTree(model: CargoProjectStructure) : SimpleTree(model) {

    val selectedProject: CargoProject? get() {
        val path = selectionPath ?: return null
        if (path.pathCount < 2) return null
        val treeNode = path.getPathComponent(1) as? DefaultMutableTreeNode ?: return null
        return (treeNode.userObject as? CargoProjectStructure.Node.Project)?.cargoProject
    }

    init {
        isRootVisible = false
        emptyText.text = "There are no Cargo projects to display."
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }
}

class CargoProjectTreeRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
        val node = (value as? DefaultMutableTreeNode)?.userObject as? CargoProjectStructure.Node ?: return
        icon = node.icon
        appendName(node)
    }

    private fun appendName(node: CargoProjectStructure.Node) {
        when (node) {
            is CargoProjectStructure.Node.Project -> {
                val cargoProject = node.cargoProject
                var attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES
                val status = cargoProject.mergedStatus
                when (status) {
                    is CargoProject.UpdateStatus.UpdateFailed -> {
                        attrs = attrs.derive(SimpleTextAttributes.STYLE_WAVED, null, null, JBColor.RED)
                        toolTipText = status.reason
                    }
                    is CargoProject.UpdateStatus.NeedsUpdate -> {
                        attrs = attrs.derive(SimpleTextAttributes.STYLE_WAVED, null, null, JBColor.GRAY)
                        toolTipText = "Project needs update"
                    }
                    is CargoProject.UpdateStatus.UpToDate -> {
                        toolTipText = "Project is up-to-date"
                    }
                }
                append(cargoProject.presentableName, attrs)
            }
            is CargoProjectStructure.Node.Target -> {
                append(node.name)
                val targetKind = node.target.kind
                if (targetKind != CargoWorkspace.TargetKind.UNKNOWN) {
                    toolTipText = "${StringUtil.capitalize(targetKind.name.toLowerCase())} target `${node.name}`"
                }
            }
            else -> append(node.name)
        }
    }
}

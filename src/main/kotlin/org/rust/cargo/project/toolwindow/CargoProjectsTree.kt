/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.toolwindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.treeStructure.SimpleTree
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.toolwindow.CargoProjectTreeStructure.CargoSimpleNode
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.stdext.capitalized
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

open class CargoProjectsTree : SimpleTree() {

    val selectedProject: CargoProject? get() {
        val path = selectionPath ?: return null
        if (path.pathCount < 2) return null
        val treeNode = path.getPathComponent(1) as? DefaultMutableTreeNode ?: return null
        return (treeNode.userObject as? CargoSimpleNode.Project)?.cargoProject
    }

    init {
        isRootVisible = false
        showsRootHandles = true
        emptyText.text = "There are no Rust projects to display."
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        @Suppress("LeakingThis")
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount < 2 || !SwingUtilities.isLeftMouseButton(e)) return
                val tree = e.source as? CargoProjectsTree ?: return
                val node = tree.selectionModel.selectionPath
                    ?.lastPathComponent as? DefaultMutableTreeNode ?: return
                val target = (node.userObject as? CargoSimpleNode.Target)?.target ?: return
                val command = target.launchCommand()
                if (command == null) {
                    LOG.warn("Can't create launch command for `${target.name}` target")
                    return
                }
                val cargoProject = selectedProject ?: return
                // Capitalize command name to be consistent with line market providers
                // TODO: not to use `capitalized` here
                val configurationName = "${command.capitalized()} ${target.name}"
                run(CargoCommandLine.forTarget(target, command), cargoProject, configurationName)
            }
        })
    }

    protected open fun run(commandLine: CargoCommandLine, project: CargoProject, name: String) {
        commandLine.run(project, name)
    }

    companion object {
        private val LOG: Logger = logger<CargoProjectsTree>()
    }
}

private fun CargoWorkspace.Target.launchCommand(): String? = when (kind) {
    CargoWorkspace.TargetKind.Bin -> "run"
    is CargoWorkspace.TargetKind.Lib -> "build"
    CargoWorkspace.TargetKind.Test -> "test"
    CargoWorkspace.TargetKind.Bench -> "bench"
    CargoWorkspace.TargetKind.ExampleBin -> "run"
    is CargoWorkspace.TargetKind.ExampleLib -> "build"
    else -> null
}

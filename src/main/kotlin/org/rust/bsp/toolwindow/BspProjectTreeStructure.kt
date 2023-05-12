/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.toolwindow

import ch.epfl.scala.bsp4j.BuildTarget
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.Disposable
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.CachingSimpleNode
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTreeStructure
import org.rust.bsp.service.BspConnectionService
import org.rust.cargo.icons.CargoIcons

class BspProjectTreeStructure(
    tree: BspProjectsTree,
    parentDisposable: Disposable
) : SimpleTreeStructure() {
    protected lateinit var bspConnection: BspConnectionService
    private val treeModel = StructureTreeModel(this, parentDisposable)
    private var root = BspSimpleNode.Root(emptyMap(), emptyList())

    init {
        tree.model = AsyncTreeModel(treeModel, parentDisposable)
    }

    override fun getRootElement(): Any = root

    fun updateBspProjects(targets: List<BuildTarget>) {
        val top = targets.map { it.id }.toMutableSet()
        for (t in targets) {
            for (d in t.dependencies) {
                top.remove(d)
            }
        }
        // TODO : We assume that targets form DAG - can we assume that
        root = BspSimpleNode.Root(targets.associateBy { it.id }, targets.filter { top.contains(it.id) })
        treeModel.invalidate()
    }

    sealed class BspSimpleNode(parent: SimpleNode?) : CachingSimpleNode(parent) {

        class Root(private val allProjects: Map<BuildTargetIdentifier, BuildTarget>, private val topTargets: List<BuildTarget>) : BspSimpleNode(null) {
            override fun buildChildren(): Array<SimpleNode> = topTargets.map { Target(it, allProjects, this) }.sortedBy { it.name }.toTypedArray()
            override fun getName(): String = ""
        }

        class Target(val target: BuildTarget, val allProjects: Map<BuildTargetIdentifier, BuildTarget>, val node: SimpleNode) : BspSimpleNode(node) {

            init {
                icon = if ("rust" in target.languageIds) CargoIcons.RUST else CargoIcons.BSP
            }

            override fun buildChildren(): Array<SimpleNode> {
                return target.dependencies.filter { allProjects.containsKey(it) }
                    .map { Target (allProjects[it]!!, allProjects, this)}.toTypedArray()
            }

            override fun getName(): String = target.displayName

            override fun update(presentation: PresentationData) {
                val attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES
                presentation.addText(target.displayName, attrs)
                presentation.setIcon(icon)
            }

        }

    }
}

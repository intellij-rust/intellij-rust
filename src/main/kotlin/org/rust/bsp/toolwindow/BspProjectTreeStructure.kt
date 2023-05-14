/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.toolwindow

import ch.epfl.scala.bsp4j.BuildTarget
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.CachingSimpleNode
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTreeStructure
import org.rust.bsp.BspConstants
import org.rust.bsp.service.BspConnectionService
import org.rust.bsp.service.BspProjectViewService
import org.rust.cargo.icons.CargoIcons
import java.awt.Color

class BspProjectTreeStructure(
    tree: BspProjectsTree,
    parentDisposable: Disposable,
    val project: Project
) : SimpleTreeStructure() {
    protected lateinit var bspConnection: BspConnectionService
    private val treeModel = StructureTreeModel(this, parentDisposable)
    private var root = BspSimpleNode.Root(mutableMapOf(), emptyList(), project)

    init {
        tree.model = AsyncTreeModel(treeModel, parentDisposable)
    }

    override fun getRootElement(): Any = root

    fun updateBspProjects(targets: List<BuildTarget>) {
        val top = targets.map { it.id }.toMutableSet()
        top.removeIf { it.uri == BspConstants.BSP_WORKSPACE_ROOT_URI }
        for (t in targets) {
            for (d in t.dependencies) {
                top.remove(d)
            }
        }
        // TODO : We assume that targets form DAG - can we assume that?

        val targetById = targets.associate { Pair(it.id.uri, Pair(it, false)) }.toMutableMap()
        val viewService = project.service<BspProjectViewService>()
        val filteredTargets = viewService.filterIncludedPackages(targets.map { it.id })
        for (targ in filteredTargets)
            if (targetById.containsKey(targ.uri))
                targetById[targ.uri] = Pair(targetById[targ.uri]!!.first, true)

        root = BspSimpleNode.Root(targetById, targets.filter { top.contains(it.id) }, project)
        treeModel.invalidate()
    }

    sealed class BspSimpleNode(parent: SimpleNode?) : CachingSimpleNode(parent) {

        class Root(private val allProjects: MutableMap<String, Pair<BuildTarget, Boolean>>, private val topTargets: List<BuildTarget>, private val projecta: Project) : BspSimpleNode(null) {
            override fun buildChildren(): Array<SimpleNode> = topTargets.map { Target(it, allProjects, this, projecta) }.sortedBy { it.name }.toTypedArray()
            override fun getName(): String = ""

        }

        class Target(val target: BuildTarget, val allProjects: MutableMap<String, Pair<BuildTarget, Boolean>>, val node: SimpleNode, private val projecta: Project) : BspSimpleNode(node) {
            val initialStatus = allProjects[target.id.uri]!!.second

            private fun getCurrentColor(): Color? {
                if (initialStatus) {
                    if (allProjects[target.id.uri]!!.second) {
                        return JBColor.BLUE
                    }
                    else {
                        return JBColor.RED
                    }
                }
                else {
                    if (allProjects[target.id.uri]!!.second) {
                        return JBColor.GREEN
                    }
                    else {
                        return null
                    }
                }
            }
            init {
                icon = if ("rust" in target.languageIds) CargoIcons.RUST else CargoIcons.BSP
                myColor = if (allProjects[target.id.uri]!!.second) JBColor.BLUE else null
            }
            fun click() {
                val viewManager = projecta.service<BspProjectViewService>()
                if (allProjects[target.id.uri]!!.second) {
                    allProjects[target.id.uri] = Pair(target, false)
                    viewManager.excludePackage(target.id)
                    myColor = getCurrentColor()
                }
                else {
                    allProjects[target.id.uri] = Pair(target, true)
                    viewManager.includePackage(target.id)
                    myColor = getCurrentColor()
                }
                presentation.forcedTextForeground = myColor

            }
            override fun buildChildren(): Array<SimpleNode> {
                return target.dependencies.filter { allProjects.containsKey(it.uri) }
                    .map { Target (allProjects[it.uri]!!.first, allProjects, this, projecta)}.toTypedArray()
            }

            override fun getName(): String = target.displayName

            override fun update(presentation: PresentationData) {
                val attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES
                presentation.addText(target.displayName, attrs)
                presentation.setIcon(icon)
                presentation.forcedTextForeground = myColor
            }

        }

    }
}

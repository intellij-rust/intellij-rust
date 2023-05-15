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
    private var targetById: MutableMap<String, Triple<BuildTarget, Boolean, Boolean>> = mutableMapOf()

    init {
        tree.model = AsyncTreeModel(treeModel, parentDisposable)
    }

    override fun getRootElement(): Any = root

    fun updateBspProjects(targets: List<BuildTarget>) {
        val top = targets.map { it.id }.toMutableSet()
        top.removeIf { it.uri == BspConstants.BSP_WORKSPACE_ROOT_URI }
        val targetsWithoutRoot = targets.filter { it.id.uri != BspConstants.BSP_WORKSPACE_ROOT_URI }

        for (t in targetsWithoutRoot) {
            for (d in t.dependencies) {
                top.remove(d)
            }
        }
        // TODO : We assume that targets form DAG - can we assume that?

        targetById = targetsWithoutRoot.associate { Pair(it.id.uri, Triple(it, false, false)) }.toMutableMap()
        val viewService = project.service<BspProjectViewService>()
        val filteredTargets = viewService.filterIncludedPackages(targets.map { it.id })
        for (targ in filteredTargets)
            if (targetById.containsKey(targ.uri))
                targetById[targ.uri] = Triple(targetById[targ.uri]!!.first, true, true)

        root = BspSimpleNode.Root(targetById, targetsWithoutRoot.filter { top.contains(it.id) }, project)
        treeModel.invalidate()
    }

    fun selectAll() {
        val viewManager = project.service<BspProjectViewService>()
        targetById.replaceAll { _: String, value: Triple<BuildTarget, Boolean, Boolean> ->
            viewManager.includePackage(value.first.id)
            return@replaceAll Triple(value.first, true, value.third)
        }
    }


    fun unselectAll() {
        val viewManager = project.service<BspProjectViewService>()
        targetById.replaceAll { _: String, value: Triple<BuildTarget, Boolean, Boolean> ->
            viewManager.excludePackage(value.first.id)
            return@replaceAll Triple(value.first, false, value.third)
        }
    }

    fun clearAll() {
        val viewManager = project.service<BspProjectViewService>()
        targetById.replaceAll { _: String, value: Triple<BuildTarget, Boolean, Boolean> ->
            if (value.third)
                viewManager.includePackage(value.first.id)
            else
                viewManager.excludePackage(value.first.id)
            return@replaceAll Triple(value.first, value.third, value.third)
        }
    }

    fun checkStatus() = root.checkStatus()

    sealed class BspSimpleNode(parent: SimpleNode?) : CachingSimpleNode(parent) {

        class Root(
            private val allProjects: MutableMap<String, Triple<BuildTarget, Boolean, Boolean>>, //triple describes for given target id (Target, is it staged for resolution, is it resolved)
            topTargets: List<BuildTarget>,
            private val projecta: Project
        ) : BspSimpleNode(null) {

            val children = topTargets.map { Target(it, allProjects, this, projecta) }.sortedBy { it.name }
            override fun buildChildren(): Array<SimpleNode> = children.toTypedArray()
            override fun getName(): String = ""

            fun checkStatus() {
                children.forEach { it.checkStatus() }
            }

        }

        class Target(
            val target: BuildTarget,
            val allProjects: MutableMap<String, Triple<BuildTarget, Boolean, Boolean>>,
            val node: SimpleNode,
            private val projecta: Project
        ) : BspSimpleNode(node) {
            val children = target.dependencies.filter { allProjects.containsKey(it.uri) }
                .map { Target(allProjects[it.uri]!!.first, allProjects, this, projecta) }

            private fun getCurrentColor(): Color? {
                val initialStatus = allProjects[target.id.uri]!!.third
                val currentStatus = allProjects[target.id.uri]!!.second
                return if (initialStatus) {
                    if (currentStatus)
                        JBColor.BLUE
                    else
                        JBColor.RED
                } else {
                    if (currentStatus)
                        JBColor.GREEN
                    else
                        null
                }
            }

            init {
                icon = if ("rust" in target.languageIds) CargoIcons.RUST else CargoIcons.BSP
                myColor = if (allProjects[target.id.uri]!!.second) JBColor.BLUE else null
            }

            fun checkStatus() {
                myColor = getCurrentColor()
                presentation.forcedTextForeground = myColor
                children.forEach { it.checkStatus() }
            }

            fun click() {
                val viewManager = projecta.service<BspProjectViewService>()
                val currentStatus = allProjects[target.id.uri]!!
                if (currentStatus.second) {
                    allProjects[target.id.uri] = Triple(target, false, currentStatus.third)
                    viewManager.excludePackage(target.id)
                } else {
                    allProjects[target.id.uri] = Triple(target, true, currentStatus.third)
                    viewManager.includePackage(target.id)
                }

            }

            override fun buildChildren(): Array<SimpleNode> {
                return children.toTypedArray()
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

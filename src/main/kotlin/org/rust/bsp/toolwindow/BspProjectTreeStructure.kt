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
    private lateinit var bspConnection: BspConnectionService
    private val treeModel = StructureTreeModel(this, parentDisposable)
    private var root = BspSimpleNode.Root(mutableMapOf(), emptyList(), project)
    private var targetById: MutableMap<String, BspNodeStatus> = mutableMapOf()

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

        targetById = targetsWithoutRoot.associate { Pair(it.id.uri, BspNodeStatus(it, isStaged = false, isResolved = false)) }.toMutableMap()
        val viewService = project.service<BspProjectViewService>()
        val filteredTargets = viewService.filterIncludedPackages(targets.map { it.id })
        for (targ in filteredTargets)
            if (targetById.containsKey(targ.uri))
                targetById[targ.uri] = BspNodeStatus(targetById[targ.uri]!!.target, true, true)

        root = BspSimpleNode.Root(targetById, targetsWithoutRoot.filter { top.contains(it.id) }, project)
        treeModel.invalidate()
    }

    fun selectAll() {
        val viewManager = project.service<BspProjectViewService>()
        targetById.forEach { (_: String, value: BspNodeStatus) ->
            viewManager.includePackage(value.target.id)
            value.setStatus(true)
        }
    }


    fun unselectAll() {
        val viewManager = project.service<BspProjectViewService>()
        targetById.forEach { (_: String, value: BspNodeStatus) ->
            viewManager.excludePackage(value.target.id)
            value.setStatus(false)
        }
    }

    fun clearAll() {
        val viewManager = project.service<BspProjectViewService>()
        targetById.forEach { (_: String, value: BspNodeStatus) ->
            if (value.isStaged != value.isResolved)
                if (value.isResolved)
                    viewManager.includePackage(value.target.id)
                else
                    viewManager.excludePackage(value.target.id)
            value.resetStatus()
        }
    }

    fun checkStatus() = root.checkStatus()

    class BspNodeStatus(
        val target: BuildTarget,
        var isStaged: Boolean,
        val isResolved: Boolean
    ) {

        fun setStatus(newStatus: Boolean) {
            isStaged = newStatus
        }

        fun resetStatus() {
            isStaged = isResolved
        }

        fun getColor(): Color? {
            return if (isResolved) {
                if (isStaged)
                    JBColor.BLUE
                else
                    JBColor.RED
            } else {
                if (isStaged)
                    JBColor.GREEN
                else
                    null
            }
        }
    }

    sealed class BspSimpleNode(parent: SimpleNode?) : CachingSimpleNode(parent) {

        class Root(
            private val allProjects: MutableMap<String, BspNodeStatus>,
            topTargets: List<BuildTarget>,
            private val projecta: Project
        ) : BspSimpleNode(null) {

            val children = topTargets.map { Target(it, allProjects, this, projecta) }.sortedBy { it.name }
            override fun buildChildren(): Array<SimpleNode> = children.toTypedArray()
            override fun getName(): String = ""

            fun checkStatus() = children.forEach { it.checkStatus() }

        }

        class Target(
            val target: BuildTarget,
            val allProjects: MutableMap<String, BspNodeStatus>,
            val node: SimpleNode,
            private val projecta: Project
        ) : BspSimpleNode(node) {
            val children = target.dependencies.filter { allProjects.containsKey(it.uri) }
                .map { Target(allProjects[it.uri]!!.target, allProjects, this, projecta) }

            private fun getCurrentColor(): Color? = allProjects[target.id.uri]!!.getColor()

            init {
                icon = if ("rust" in target.languageIds) CargoIcons.RUST else CargoIcons.BSP
                myColor = if (allProjects[target.id.uri]!!.isStaged) JBColor.BLUE else null
            }

            fun checkStatus() {
                myColor = getCurrentColor()
                presentation.forcedTextForeground = myColor
                children.forEach { it.checkStatus() }
            }

            fun click() {
                val viewManager = projecta.service<BspProjectViewService>()
                val currentStatus = allProjects[target.id.uri]!!
                if (currentStatus.isStaged) {
                    allProjects[target.id.uri]!!.setStatus(false)
                    viewManager.excludePackage(target.id)
                } else {
                    allProjects[target.id.uri]!!.setStatus(true)
                    viewManager.includePackage(target.id)
                }

            }

            override fun buildChildren(): Array<SimpleNode> = children.toTypedArray()

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

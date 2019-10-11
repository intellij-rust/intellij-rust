/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.toolwindow

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.CachingSimpleNode
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTreeStructure
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.TargetKind.*
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.command.workingDirectory
import javax.swing.Icon

class CargoProjectTreeStructure(
    tree: CargoProjectsTree,
    parentDisposable: Disposable,
    private var cargoProjects: List<CargoProject> = emptyList()
) : SimpleTreeStructure() {

    private val treeModel = StructureTreeModel(this, parentDisposable)
    private var root = CargoSimpleNode.Root(cargoProjects)

    init {
        tree.model = AsyncTreeModel(treeModel, parentDisposable)
    }

    override fun getRootElement(): Any = root

    fun updateCargoProjects(cargoProjects: List<CargoProject>) {
        this.cargoProjects = cargoProjects
        root = CargoSimpleNode.Root(cargoProjects)
        treeModel.invalidate()
    }

    sealed class CargoSimpleNode(parent: SimpleNode?) : CachingSimpleNode(parent) {
        abstract fun toTestString(): String

        class Root(private val cargoProjects: List<CargoProject>) : CargoSimpleNode(null) {
            override fun buildChildren(): Array<SimpleNode> = cargoProjects.map { Project(it, this) }.toTypedArray()
            override fun getName(): String = ""
            override fun toTestString(): String = "Root"
        }

        class Project(val cargoProject: CargoProject, parent: SimpleNode) : CargoSimpleNode(parent) {

            init {
                icon = CargoIcons.ICON
            }

            override fun buildChildren(): Array<SimpleNode> {
                val (ourPackage, workspaceMembers) = cargoProject.workspace
                    ?.packages
                    ?.filter { it.origin == PackageOrigin.WORKSPACE }
                    .orEmpty()
                    .partition { it.rootDirectory == cargoProject.workingDirectory }
                val childrenNodes = mutableListOf<SimpleNode>()
                ourPackage.mapTo(childrenNodes) { Targets(it.targets, this) }
                workspaceMembers.mapTo(childrenNodes) { WorkspaceMember(it, this) }
                return childrenNodes.toTypedArray()
            }

            override fun getName(): String = cargoProject.presentableName

            override fun update(presentation: PresentationData) {
                var attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES
                when (val status = cargoProject.mergedStatus) {
                    is CargoProject.UpdateStatus.UpdateFailed -> {
                        attrs = attrs.derive(SimpleTextAttributes.STYLE_WAVED, null, null, JBColor.RED)
                        presentation.tooltip = status.reason
                    }
                    is CargoProject.UpdateStatus.NeedsUpdate -> {
                        attrs = attrs.derive(SimpleTextAttributes.STYLE_WAVED, null, null, JBColor.GRAY)
                        presentation.tooltip = "Project needs update"
                    }
                    is CargoProject.UpdateStatus.UpToDate -> {
                        presentation.tooltip = "Project is up-to-date"
                    }
                }
                presentation.addText(cargoProject.presentableName, attrs)
                presentation.setIcon(icon)
            }

            override fun toTestString(): String = "Project($name)"
        }

        class WorkspaceMember(val pkg: CargoWorkspace.Package, parent: SimpleNode) : CargoSimpleNode(parent) {

            init {
                icon = CargoIcons.ICON
            }

            override fun buildChildren(): Array<SimpleNode> = arrayOf(Targets(pkg.targets, this))
            override fun getName(): String = pkg.name
            override fun toTestString(): String = "WorkspaceMember($name)"
        }

        class Targets(val targets: Collection<CargoWorkspace.Target>, parent: SimpleNode) : CargoSimpleNode(parent) {

            init {
                icon = CargoIcons.TARGETS
            }

            override fun buildChildren(): Array<SimpleNode> = targets.map { Target(it, this) }.toTypedArray()
            override fun getName(): String = "targets"
            override fun toTestString(): String = "Targets"
        }

        class Target(val target: CargoWorkspace.Target, parent: SimpleNode) : CargoSimpleNode(parent) {

            init {
                icon = target.icon
            }

            override fun buildChildren(): Array<SimpleNode> = emptyArray()
            override fun getName(): String = target.name
            override fun update(presentation: PresentationData) {
                super.update(presentation)
                val targetKind = target.kind
                if (targetKind != Unknown) {
                    presentation.tooltip = "${StringUtil.capitalize(targetKind.name)} target `${name}`"
                }
            }

            override fun toTestString(): String = "Target($name[${target.kind.name.toLowerCase()}])"

            private val CargoWorkspace.Target.icon: Icon?
                get() = when (kind) {
                    is Lib -> CargoIcons.LIB_TARGET
                    Bin -> CargoIcons.BIN_TARGET
                    Test -> CargoIcons.TEST_TARGET
                    Bench -> CargoIcons.BENCH_TARGET
                    ExampleBin, is ExampleLib -> CargoIcons.EXAMPLE_TARGET
                    Unknown -> null
                }
        }
    }
}

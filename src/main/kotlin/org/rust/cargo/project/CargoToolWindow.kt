/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColorUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UIUtil
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.*
import org.rust.cargo.project.model.CargoProject.UpdateStatus
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.ListSelectionModel


class CargoToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        guessAndSetupRustProject(project)
        val toolwindowPanel = CargoToolWindowPanel(project)
        val tab = ContentFactory.SERVICE.getInstance()
            .createContent(toolwindowPanel, "", false)
        toolWindow.contentManager.addContent(tab)
    }
}

private class CargoToolWindowPanel(project: Project) : SimpleToolWindowPanel(true, false) {
    private val cargoTab = CargoToolWindow(project)

    init {
        setToolbar(cargoTab.toolbar.component)
        cargoTab.toolbar.setTargetComponent(this)
        setContent(cargoTab.content)
    }

    override fun getData(dataId: String): Any? {
        if (DetachCargoProjectAction.CARGO_PROJECT_TO_DETACH.`is`(dataId)) {
            return cargoTab.selectedProject
        }
        return super.getData(dataId)
    }
}

private class CargoToolWindow(
    private val project: Project
) {
    private var _cargoProjects: List<CargoProject> = emptyList()
    private var cargoProjects: List<CargoProject>
        get() = _cargoProjects
        set(value) {
            check(ApplicationManager.getApplication().isDispatchThread)
            _cargoProjects = value
            projectList.setListData(cargoProjects.toTypedArray())
        }

    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("Cargo Toolbar", actionManager.getAction("Rust.Cargo") as DefaultActionGroup, true)
    }

    val note = JEditorPane("text/html", html("")).apply {
        background = UIUtil.getTreeBackground()
        isEditable = false
    }
    private val projectList = JBList<CargoProject>(emptyList()).apply {
        emptyText.text = "There are no Cargo projects to display."
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : ColoredListCellRenderer<CargoProject>() {
            override fun customizeCellRenderer(list: JList<out CargoProject>, value: CargoProject, index: Int, selected: Boolean, hasFocus: Boolean) {
                icon = CargoIcons.ICON
                var attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES
                val status = value.mergedStatus
                when (status) {
                    is UpdateStatus.UpdateFailed -> {
                        attrs = attrs.derive(SimpleTextAttributes.STYLE_WAVED, null, null, JBColor.RED)
                        toolTipText = status.reason
                    }
                    is UpdateStatus.NeedsUpdate -> {
                        attrs = attrs.derive(SimpleTextAttributes.STYLE_WAVED, null, null, JBColor.GRAY)
                        toolTipText = "Project needs update"
                    }
                    is UpdateStatus.UpToDate -> {
                        toolTipText = "Project is up-to-date"
                    }
                }
                append(value.presentableName, attrs)
            }
        }
    }
    val selectedProject: CargoProject? get() = projectList.selectedValue

    val content = panel {
        row {
            projectList(CCFlags.push, CCFlags.grow)
        }
    }

    init {
        with(project.messageBus.connect()) {
            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
                override fun cargoProjectsUpdated(projects: Collection<CargoProject>) {
                    ApplicationManager.getApplication().invokeLater {
                        cargoProjects = projects.sortedBy { it.manifest }
                    }
                }
            })
        }

        ApplicationManager.getApplication().invokeLater {
            cargoProjects = project.cargoProjects.allProjects.sortedBy { it.manifest }
        }
    }

    override fun toString(): String {
        return "CargoToolWindow(workspaces = $_cargoProjects)"
    }

    private fun html(body: String): String = """
        <html>
        <head>
            ${UIUtil.getCssFontDeclaration(UIUtil.getLabelFont())}
            <style>body {background: #${ColorUtil.toHex(UIUtil.getTreeBackground())}; text-align: center; }</style>
        </head>
        <body>
            $body
        </body>
        </html>
    """
}

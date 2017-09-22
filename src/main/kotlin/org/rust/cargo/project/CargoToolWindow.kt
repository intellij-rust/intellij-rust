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
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UIUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProject.UpdateStatus
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.cargoProjects
import javax.swing.JEditorPane


class CargoToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolwindowPanel = run {
            val cargoTab = CargoToolWindow(project)
            SimpleToolWindowPanel(true, false).apply {
                setToolbar(cargoTab.toolbar.component)
                cargoTab.toolbar.setTargetComponent(this)
                setContent(cargoTab.content)
            }
        }

        val tab = ContentFactory.SERVICE.getInstance()
            .createContent(toolwindowPanel, "", false)
        toolWindow.contentManager.addContent(tab)
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
            updateUi()
        }

    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("Cargo Toolbar", actionManager.getAction("Rust.Cargo") as DefaultActionGroup, true)
    }

    val note = JEditorPane("text/html", html("")).apply {
        background = UIUtil.getTreeBackground()
        isEditable = false
    }
    val content = panel {
        row { note(CCFlags.push, CCFlags.grow) }
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

        updateData()
    }

    private fun updateData() {
        ApplicationManager.getApplication().invokeLater {
            cargoProjects = project.cargoProjects.allProjects.sortedBy { it.manifest }
        }
    }

    private fun updateUi() {
        note.text = if (cargoProjects.isEmpty()) {
            html("There are no Cargo projects to display.")
        } else {
            html(buildString {
                for (project in cargoProjects) {
                    val status = project.mergedStatus
                    when (status) {
                        is UpdateStatus.UpdateFailed ->
                            append("Project ${project.presentableName} failed to update!")

                        is UpdateStatus.NeedsUpdate ->
                            append("Project ${project.presentableName} needs update.")

                        is UpdateStatus.UpToDate ->
                            append("Project ${project.presentableName} is up-to-date.")
                    }
                    append("</br>")
                }
            })
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

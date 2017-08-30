/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.ProjectTopics
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColorUtil
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UIUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.cargo.project.workspace.impl.CargoTomlWatcher
import org.rust.cargo.util.modulesWithCargoProject
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
    private var _cargoProjects: List<Pair<Module, CargoWorkspace?>> = emptyList()
    private var cargoProjects: List<Pair<Module, CargoWorkspace?>>
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
            subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent?) {
                    updateData()
                }
            })
            subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { CargoTomlWatcher.isCargoTomlChange(it) }) {
                        updateData()
                    }
                }

                override fun before(events: List<VFileEvent>) {}
            })
        }

        updateData()
    }

    private fun updateData() {
        ApplicationManager.getApplication().invokeLater {
            cargoProjects = project.modulesWithCargoProject.map { module ->
                module to module.cargoWorkspace
            }
        }
    }

    private fun updateUi() {
        note.text = if (cargoProjects.isEmpty()) {
            html("There are no Cargo projects to display.")
        } else {
            html(buildString {
                for ((module, ws) in cargoProjects) {
                    if (ws != null) {
                        val projectName = ws.manifestPath?.parent?.fileName?.toString()
                        if (projectName != null) append("Project $projectName up-to-date.")
                    } else {
                        append("Project ${module.name} failed to update!")
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

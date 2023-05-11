/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.toolwindow

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.ui.ColorUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import org.rust.bsp.service.BspConnectionService
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.guessAndSetupRustProject
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.runconfig.hasCargoProject
import javax.swing.JComponent
import javax.swing.JEditorPane

class BspToolWindowFactory : ToolWindowFactory, DumbAware {
    private val lock: Any = Any()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        guessAndSetupRustProject(project)
        val toolwindowPanel = BspToolWindowPanel(project)
        val tab = ContentFactory.getInstance()
            .createContent(toolwindowPanel, "", false)
        toolWindow.contentManager.addContent(tab)
    }

    override fun isApplicable(project: Project): Boolean {
        return project.service<BspConnectionService>().hasBspServer();
    }
}

private class BspToolWindowPanel(project: Project) : SimpleToolWindowPanel(true, false) {
    private val bspTab = BspToolWindow(project)

    init {
        toolbar = bspTab.toolbar.component
        bspTab.toolbar.targetComponent = this
        setContent(bspTab.content)
    }

    override fun getData(dataId: String): Any? =
        when {
            PlatformDataKeys.TREE_EXPANDER.`is`(dataId) -> bspTab.treeExpander
            else -> super.getData(dataId)
        }
}

class BspToolWindow(
    private val project: Project
) {
    private val bspService: BspConnectionService = project.service<BspConnectionService>()
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar(BSP_TOOLBAR_PLACE, actionManager.getAction("Rust.Bsp") as DefaultActionGroup, true)
    }

    val note = JEditorPane("text/html", html("")).apply {
        background = UIUtil.getTreeBackground()
        isEditable = false
    }

    private val projectTree = BspProjectsTree()
    private val projectStructure = BspProjectTreeStructure(projectTree, project)

    val treeExpander: TreeExpander


    val content: JComponent = ScrollPaneFactory.createScrollPane(projectTree, 0)

    init {
        treeExpander = object : DefaultTreeExpander(projectTree) {
            override fun isCollapseAllVisible(): Boolean = bspService.hasBspServer()
            override fun isExpandAllVisible(): Boolean = bspService.hasBspServer()
        }

        with(project.messageBus.connect()) {
            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, CargoProjectsListener { _, projects ->
                invokeLater {
                    projectStructure.updateCargoProjects(projects.toList())
                }
            })
        }

        invokeLater {
            projectStructure.updateCargoProjects(project.cargoProjects.allProjects.toList())
        }
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

    companion object {
        private val LOG: Logger = logger<BspToolWindow>()

        const val BSP_TOOLBAR_PLACE: String = "Bsp Toolbar"

        private const val ID: String = "BSP"
    }
}

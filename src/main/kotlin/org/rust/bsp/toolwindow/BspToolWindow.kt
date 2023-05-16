package org.rust.bsp.toolwindow

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColorUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import org.rust.bsp.service.BspConnectionService
import org.rust.bsp.toolwindow.actions.ClearAllAction
import org.rust.bsp.toolwindow.actions.UnselectAllAction
import org.rust.bsp.toolwindow.actions.SelectAllAction
import org.rust.cargo.project.model.guessAndSetupRustProject
import javax.swing.JComponent
import javax.swing.JEditorPane

class BspToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        guessAndSetupRustProject(project)
        val toolwindowPanel = BspToolWindowPanel(project)
        val tab = ContentFactory.getInstance()
            .createContent(toolwindowPanel, "", false)
        toolWindow.contentManager.addContent(tab)
    }

    override fun isApplicable(project: Project): Boolean = project.service<BspConnectionService>().hasBspServer()

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

    private val projectTree = BspProjectsTree()
    private val projectStructure = BspProjectTreeStructure(projectTree, bspService, project)

    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction("Rust.Bsp") as DefaultActionGroup
        actionGroup.add(SelectAllAction(projectTree, projectStructure))
        actionGroup.add(UnselectAllAction(projectTree, projectStructure))
        actionGroup.add(ClearAllAction(projectTree, projectStructure))
        actionManager.createActionToolbar(BSP_TOOLBAR_PLACE, actionGroup, true)
    }

    val note = JEditorPane("text/html", html("")).apply {
        background = UIUtil.getTreeBackground()
        isEditable = false
    }


    val treeExpander: TreeExpander


    val content: JComponent = ScrollPaneFactory.createScrollPane(projectTree, 0)

    init {
        treeExpander = object : DefaultTreeExpander(projectTree) {
            override fun isCollapseAllVisible(): Boolean = bspService.hasBspServer()
            override fun isExpandAllVisible(): Boolean = bspService.hasBspServer()
        }

        with(project.messageBus.connect()) {
            subscribe(BspConnectionService.BSP_WORKSPACE_REFRESH_TOPIC, BspConnectionService.BspProjectsRefreshListener {
                projectStructure.updateBspProjects(project.service<BspConnectionService>().getBspTargets())
            })
        }

        invokeLater {
            projectStructure.updateBspProjects(project.service<BspConnectionService>().getBspTargets())
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

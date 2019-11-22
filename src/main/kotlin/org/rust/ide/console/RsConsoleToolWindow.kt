/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory

class RsConsoleToolWindow(private val project: Project) {

    private var isInitialized: Boolean = false
    private var isStateListenerInitialized: Boolean = false

    fun initStateListener() {
        if (isStateListenerInitialized) return
        isStateListenerInitialized = true

        project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged() {
                val toolWindow = toolWindow ?: return

                val visible = toolWindow.isVisible
                if (visible && toolWindow.contentManager.contentCount == 0) {
                    val runner = RsConsoleRunnerFactory.getInstance().createConsoleRunner(project, null)
                    runner.run(true)
                }
            }
        })
    }

    fun setContent(contentDescriptor: RunContentDescriptor) {
        val toolWindow = toolWindow
            ?: error("toolWindow is null inside setContent function, which is called after toolWindow is initialized")
        setContent(toolWindow, contentDescriptor)
        doInit(toolWindow)
    }

    private fun doInit(toolWindow: ToolWindow) {
        if (!isInitialized) {
            isInitialized = true
            toolWindow.isToHideOnEmptyContent = true
        }
    }

    fun hide() {
        toolWindow?.hide(null)
    }

    private val toolWindow: ToolWindow?
        get() = ToolWindowManager.getInstance(project).getToolWindow(RsConsoleToolWindowFactory.ID)

    companion object {
        private val CONTENT_DESCRIPTOR: Key<RunContentDescriptor> = Key("CONTENT_DESCRIPTOR")

        fun getInstance(project: Project): RsConsoleToolWindow = project.getComponent(RsConsoleToolWindow::class.java)

        private fun setContent(toolWindow: ToolWindow, contentDescriptor: RunContentDescriptor) {
            toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")

            var content: Content? = toolWindow.contentManager.findContent(contentDescriptor.displayName)
            if (content == null) {
                content = createContent(contentDescriptor)
                toolWindow.contentManager.addContent(content)
            } else {
                val panel = SimpleToolWindowPanel(false, true)
                resetContent(contentDescriptor, panel, content)
            }

            toolWindow.contentManager.setSelectedContent(content)
        }

        private fun createContent(contentDescriptor: RunContentDescriptor): Content {
            val panel = SimpleToolWindowPanel(false, true)

            val content =
                ContentFactory.SERVICE.getInstance().createContent(panel, contentDescriptor.displayName, false)
            content.isCloseable = true

            resetContent(contentDescriptor, panel, content)

            return content
        }

        private fun resetContent(
            contentDescriptor: RunContentDescriptor,
            panel: SimpleToolWindowPanel,
            content: Content
        ) {
            val oldDescriptor = content.disposer as? RunContentDescriptor
            if (oldDescriptor != null) Disposer.dispose(oldDescriptor)

            panel.setContent(contentDescriptor.component)

            content.component = panel
            content.disposer = contentDescriptor
            content.preferredFocusableComponent = contentDescriptor.component

            content.putUserData(CONTENT_DESCRIPTOR, contentDescriptor)
        }
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.actions.EOFAction
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.MessageCategory
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.Cargo
import org.rust.openapiext.saveAllDocuments
import java.awt.BorderLayout
import java.nio.charset.StandardCharsets
import javax.swing.BorderFactory
import javax.swing.JPanel

class RsConsoleRunner(project: Project) :
    AbstractConsoleRunnerWithHistory<RsConsoleView>(project, TOOL_WINDOW_TITLE, null) {

    private lateinit var commandLine: GeneralCommandLine
    private lateinit var consoleCommunication: RsConsoleCommunication

    override fun getConsoleExecuteActionHandler(): RsConsoleExecuteActionHandler {
        return super.getConsoleExecuteActionHandler() as RsConsoleExecuteActionHandler
    }

    override fun getProcessHandler(): RsConsoleProcessHandler? {
        return super.getProcessHandler() as RsConsoleProcessHandler?
    }

    override fun createConsoleView(): RsConsoleView {
        val consoleView = RsConsoleView(project)
        consoleCommunication = RsConsoleCommunication(consoleView)
        return consoleView
    }

    override fun createContentDescriptorAndActions() {
        val actionManager = ActionManager.getInstance()

        val runActionGroup = DefaultActionGroup()
        val runToolbar = actionManager.createActionToolbar("RustConsoleRunner", runActionGroup, false)

        val outputActionGroup = DefaultActionGroup()
        val outputToolbar = actionManager.createActionToolbar("RustConsoleRunner", outputActionGroup, false).apply {
            val emptyBorderSize = component.border.getBorderInsets(component).left
            val outsideBorder = BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border())
            val insideBorder = JBEmptyBorder(emptyBorderSize)
            component.border = BorderFactory.createCompoundBorder(outsideBorder, insideBorder)
        }

        val actionsPanel = JPanel(BorderLayout()).apply {
            add(runToolbar.component, BorderLayout.WEST)
            add(outputToolbar.component, BorderLayout.CENTER)
        }

        val mainPanel = JPanel(BorderLayout()).apply {
            add(actionsPanel, BorderLayout.WEST)
            add(consoleView.component, BorderLayout.CENTER)
            runToolbar.setTargetComponent(this)
            outputToolbar.setTargetComponent(this)
        }

        val title = constructConsoleTitle(consoleTitle)
        val contentDescriptor = RunContentDescriptor(consoleView, processHandler, mainPanel, title, consoleIcon).apply {
            setFocusComputable { consoleView.consoleEditor.contentComponent }
            isAutoFocusContent = isAutoFocusContent
            Disposer.register(project, this)
        }

        val runActions = listOf(
            RestartAction(this),
            createConsoleExecAction(consoleExecuteActionHandler),
            StopAction(processHandler!!),
            CloseAction(executor, contentDescriptor, project)
        )
        runActionGroup.addAll(runActions)

        val outputActions = listOf<AnAction>(
            SoftWrapAction(consoleView),
            ScrollToTheEndToolbarAction(consoleView.editor),
            PrintAction(consoleView),
            ConsoleHistoryController.getController(consoleView)!!.browseHistory
        )
        outputActionGroup.addAll(outputActions)

        val actions = outputActions + runActions + EOFAction()
        registerActionShortcuts(actions, consoleView.consoleEditor.component)
        registerActionShortcuts(actions, mainPanel)

        showConsole(executor, contentDescriptor)
    }

    fun runSync(requestEditorFocus: Boolean) {
        if (Cargo.checkNeedInstallEvcxr(project)) return

        try {
            initAndRun()
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Connecting to Console", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Connecting to console..."
                    connect()
                    if (requestEditorFocus) {
                        consoleView?.requestFocus()
                    }
                }
            })
        } catch (e: Exception) {
            LOG.warn("Error running console", e)
            showErrorsInConsole(e)
        }
    }

    fun run(requestEditorFocus: Boolean) {
        if (Cargo.checkNeedInstallEvcxr(project)) return
        // BACKCOMPAT: 2019.3
        @Suppress("DEPRECATION")
        TransactionGuard.submitTransaction(project, Runnable { saveAllDocuments() })

        ApplicationManager.getApplication().executeOnPooledThread {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Connecting to Console", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Connecting to console..."
                    try {
                        initAndRun()
                        connect()
                        if (requestEditorFocus) {
                            consoleView?.requestFocus()
                        }
                    } catch (e: Exception) {
                        LOG.warn("Error running console", e)
                        invokeAndWaitIfNeeded { showErrorsInConsole(e) }
                    }
                }
            })
        }
    }

    override fun initAndRun() {
        invokeAndWaitIfNeeded {
            super.initAndRun()
        }
    }

    override fun createProcessHandler(process: Process): OSProcessHandler =
        RsConsoleProcessHandler(
            process,
            consoleView,
            consoleCommunication,
            commandLine.commandLineString,
            StandardCharsets.UTF_8
        )

    private fun createCommandLine(): GeneralCommandLine {
        val cargoProject = project.cargoProjects.allProjects.firstOrNull()
            ?: throw RuntimeException("No cargo project")
        val toolchain = project.toolchain
            ?: throw RuntimeException("Rust toolchain is not defined")
        val evcxr = toolchain.evcxr()
            ?: throw RuntimeException("Evcxr executable not found")

        val workingDir = cargoProject.workingDirectory
        return evcxr.createCommandLine(workingDir.toFile())
    }

    override fun createProcess(): Process {
        commandLine = createCommandLine()
        return commandLine.createProcess()
    }

    private fun connect() {
        invokeLater {
            consoleView.executeActionHandler = consoleExecuteActionHandler

            consoleExecuteActionHandler.isEnabled = true

            consoleView.initialized()
        }
    }

    override fun createExecuteActionHandler(): RsConsoleExecuteActionHandler {
        val consoleExecuteActionHandler =
            RsConsoleExecuteActionHandler(processHandler!!, consoleCommunication)
        consoleExecuteActionHandler.isEnabled = false
        ConsoleHistoryController(RsConsoleRootType.instance, "", consoleView).install()
        return consoleExecuteActionHandler
    }

    fun rerun() {
        object : Task.Backgroundable(project, "Restarting Console", true) {
            override fun run(indicator: ProgressIndicator) {
                val processHandler = processHandler
                if (processHandler != null) {
                    processHandler.destroyProcess()
                    processHandler.waitFor()
                }

                runInEdt {
                    RsConsoleRunner(project).run(true)
                }
            }
        }.queue()
    }

    private fun showErrorsInConsole(e: Exception) {
        val actionGroup = DefaultActionGroup(RestartAction(this))

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("RsConsoleRunnerErrors", actionGroup, false)

        // Runner creating
        val panel = JPanel(BorderLayout())
        panel.add(actionToolbar.component, BorderLayout.WEST)

        val errorViewPanel = NewErrorTreeViewPanel(project, null, false, false, null)

        val messages = mutableListOf("Can't start evcxr.")
        val message = e.message
        if (message != null && message.isNotBlank()) {
            messages += message.lines()
        }

        errorViewPanel.addMessage(MessageCategory.ERROR, messages.toTypedArray(), null, -1, -1, null)
        panel.add(errorViewPanel, BorderLayout.CENTER)

        val contentDescriptor = RunContentDescriptor(null, processHandler, panel, "Error running console")

        showConsole(executor, contentDescriptor)
    }

    companion object {
        const val TOOL_WINDOW_TITLE: String = "Rust REPL"
        val LOG: Logger = Logger.getInstance(RsConsoleRunner::class.java)
    }
}

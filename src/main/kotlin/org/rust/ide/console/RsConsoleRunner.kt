/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.Executor
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.GuiUtils
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.MessageCategory
import com.intellij.util.ui.UIUtil
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
        val defaultExecutor = executor
        val runToolbarActions = DefaultActionGroup()
        val runActionsToolbar = ActionManager.getInstance().createActionToolbar("RustConsoleRunner", runToolbarActions, false)

        val outputToolbarActions = DefaultActionGroup()
        val outputActionsToolbar = ActionManager.getInstance().createActionToolbar("RustConsoleRunner", outputToolbarActions, false)

        val actionsPanel = JPanel(BorderLayout())
        // Left toolbar panel
        actionsPanel.add(runActionsToolbar.component, BorderLayout.WEST)
        // Add line between toolbar panels
        val outputActionsComponent = outputActionsToolbar.component
        val emptyBorderSize = outputActionsComponent.border.getBorderInsets(outputActionsComponent).left
        outputActionsComponent.border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()), JBEmptyBorder(emptyBorderSize))
        // Right toolbar panel
        actionsPanel.add(outputActionsComponent, BorderLayout.CENTER)

        // Runner creating
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(actionsPanel, BorderLayout.WEST)
        mainPanel.add(consoleView.component, BorderLayout.CENTER)

        runActionsToolbar.setTargetComponent(mainPanel)
        outputActionsToolbar.setTargetComponent(mainPanel)

        val contentDescriptor = RunContentDescriptor(consoleView, processHandler, mainPanel, constructConsoleTitle(consoleTitle), consoleIcon)
        Disposer.register(project, contentDescriptor)
        contentDescriptor.setFocusComputable { consoleView.consoleEditor.contentComponent }
        contentDescriptor.isAutoFocusContent = isAutoFocusContent

        // tool bar actions
        val actions: MutableList<AnAction> = fillRunActionsToolbar(defaultExecutor, runToolbarActions, contentDescriptor)
        val outputActions: List<AnAction> = fillOutputActionsToolbar(outputToolbarActions)
        actions.addAll(outputActions)

        registerActionShortcuts(actions, consoleView.consoleEditor.component)
        registerActionShortcuts(actions, mainPanel)

        showConsole(defaultExecutor, contentDescriptor)
    }

    private fun fillRunActionsToolbar(
        defaultExecutor: Executor,
        toolbarActions: DefaultActionGroup,
        contentDescriptor: RunContentDescriptor
    ): MutableList<AnAction> {
        val actions = arrayListOf<AnAction>(
            RestartAction(this),
            createConsoleExecAction(consoleExecuteActionHandler),
            StopAction(processHandler!!),
            CloseAction(defaultExecutor, contentDescriptor, project)
        )
        toolbarActions.addAll(actions)

        // Actions without icons
        actions.add(EOFAction())

        return actions
    }

    private fun fillOutputActionsToolbar(toolbarActions: DefaultActionGroup): List<AnAction> {
        val actions: MutableList<AnAction> = ArrayList()
        // Use soft wraps
        actions.add(SoftWrapAction(consoleView))
        // Scroll to the end
        actions.add(ScrollToTheEndToolbarAction(consoleView.editor))
        // Print
        actions.add(PrintAction(consoleView))
        // Show Variables
        actions.add(ShowVarsAction(consoleView))

        // Console History
        actions.add(ConsoleHistoryController.getController(consoleView).browseHistory)
        toolbarActions.addAll(actions)
        return actions
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
                        UIUtil.invokeAndWaitIfNeeded(Runnable { showErrorsInConsole(e) })
                    }
                }
            })
        }
    }

    override fun initAndRun() {
        UIUtil.invokeAndWaitIfNeeded(Runnable {
            super.initAndRun()
        })
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
        ApplicationManager.getApplication().invokeLater {
            consoleView.initVariablesWindow()
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

                GuiUtils.invokeLaterIfNeeded({
                    RsConsoleRunner(project).run(true)
                }, ModalityState.defaultModalityState())
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
        const val TOOL_WINDOW_TITLE: String = "Rust Console"
        val LOG: Logger = Logger.getInstance(RsConsoleRunner::class.java)
    }
}

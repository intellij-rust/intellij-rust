/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.Executor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.GuiUtils
import com.intellij.util.ui.MessageCategory
import com.intellij.util.ui.UIUtil
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.Cargo
import org.rust.openapiext.saveAllDocuments
import java.awt.BorderLayout
import java.nio.charset.StandardCharsets
import javax.swing.JPanel

class RsConsoleRunner(project: Project, title: String) :
    AbstractConsoleRunnerWithHistory<RsConsoleView>(project, title, null) {

    private lateinit var commandLine: GeneralCommandLine
    private val consoleCommunication: RsConsoleCommunication = RsConsoleCommunication()

    val commandHistory: CommandHistory = CommandHistory()

    override fun getConsoleExecuteActionHandler(): RsConsoleExecuteActionHandler {
        return super.getConsoleExecuteActionHandler() as RsConsoleExecuteActionHandler
    }

    override fun getProcessHandler(): RsConsoleProcessHandler? {
        return super.getProcessHandler() as RsConsoleProcessHandler?
    }

    override fun createConsoleView(): RsConsoleView {
        val consoleView = RsConsoleView(project)

        val consoleEditor = consoleView.consoleEditor
        val historyKeyListener = HistoryKeyListener(project, consoleEditor, commandHistory)
        consoleEditor.contentComponent.addKeyListener(historyKeyListener)
        commandHistory.listeners.add(historyKeyListener)

        return consoleView
    }

    override fun showConsole(defaultExecutor: Executor, contentDescriptor: RunContentDescriptor) {
        RsConsoleToolWindow.getInstance(project).setContent(contentDescriptor)
    }

    override fun fillToolBarActions(
        toolbarActions: DefaultActionGroup,
        defaultExecutor: Executor,
        contentDescriptor: RunContentDescriptor
    ): MutableList<AnAction> {
        val actionList = arrayListOf<AnAction>(
            createConsoleExecAction(consoleExecuteActionHandler),
            RestartAction(this)
        )
        toolbarActions.addAll(actionList)
        return actionList
    }

    fun runSync(requestEditorFocus: Boolean) {
        if (checkNeedInstallEvcxr()) return

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
        if (checkNeedInstallEvcxr()) return
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

    private fun checkNeedInstallEvcxr(): Boolean {
        val needInstallEvcxr = Cargo.checkNeedInstallEvcxr(project)
        if (needInstallEvcxr) {
            ApplicationManager.getApplication().invokeLater {
                RsConsoleToolWindow.getInstance(project).hide()
            }
        }
        return needInstallEvcxr
    }

    override fun initAndRun() {
        UIUtil.invokeAndWaitIfNeeded(Runnable {
            super.initAndRun()

            consoleExecuteActionHandler.sendText(":opt 0\n")
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
            consoleView.executeActionHandler = consoleExecuteActionHandler

            consoleExecuteActionHandler.isEnabled = true

            consoleView.initialized()
        }
    }

    override fun createExecuteActionHandler(): RsConsoleExecuteActionHandler {
        val consoleExecuteActionHandler =
            RsConsoleExecuteActionHandler(processHandler!!, this, consoleCommunication, consoleView)
        consoleExecuteActionHandler.isEnabled = false
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
                    val runner = RsConsoleRunnerFactory.getInstance().createConsoleRunner(project, null)
                    runner.run(true)
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
        val LOG: Logger = Logger.getInstance(RsConsoleRunner::class.java)
    }
}

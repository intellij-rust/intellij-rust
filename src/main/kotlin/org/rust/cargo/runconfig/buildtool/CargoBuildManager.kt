/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildContentManager
import com.intellij.build.BuildViewManager
import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.SystemNotifications
import com.intellij.ui.content.MessageView
import com.intellij.util.concurrency.FutureResult
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.UIUtil
import org.rust.cargo.runconfig.CargoRunState
import org.rust.cargo.runconfig.addFormatJsonOption
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.util.CargoArgsParser
import org.rust.openapiext.saveAllDocuments
import java.util.concurrent.Future

@Suppress("UnstableApiUsage")
object CargoBuildManager {
    private val LOG_NOTIFICATION_GROUP: NotificationGroup = NotificationGroup.logOnlyGroup("Build Log")

    private val BUILDABLE_COMMANDS: List<String> = listOf("run", "test")

    @JvmField
    val CANCELED_BUILD_RESULT: Future<CargoBuildResult> =
        FutureResult(CargoBuildResult(succeeded = false, canceled = true, started = 0))

    fun build(buildConfiguration: CargoBuildConfiguration): Future<CargoBuildResult> {
        val state = CargoRunState(
            buildConfiguration.environment,
            buildConfiguration.configuration,
            buildConfiguration.configuration.clean().ok ?: return CANCELED_BUILD_RESULT
        ).apply {
            addCommandLinePatch(cargoBuildPatch)
            for (patch in buildConfiguration.environment.cargoPatches) {
                addCommandLinePatch(patch)
            }
        }

        val cargoProject = state.cargoProject ?: return CANCELED_BUILD_RESULT

        // Make sure build tool window is initialized:
        ServiceManager.getService(cargoProject.project, BuildContentManager::class.java)

        return execute(CargoBuildContext(
            cargoProject = cargoProject,
            environment = buildConfiguration.environment,
            taskName = "Build",
            progressTitle = "Building...",
            isTestBuild = buildConfiguration.configuration.command == "test"
        )) {
            val viewManager = ServiceManager.getService(project, BuildViewManager::class.java)
            if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
                val buildToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.BUILD)
                buildToolWindow?.setAvailable(true, null)
                buildToolWindow?.show(null)
            }

            processHandler = state.startProcess(emulateTerminal = true)
            processHandler.addProcessListener(CargoBuildListener(this, viewManager))
            processHandler.startNotify()
        }
    }

    private fun execute(
        context: CargoBuildContext,
        doExecute: CargoBuildContext.() -> Unit
    ): FutureResult<CargoBuildResult> {
        val processCreationLock = Any()

        if (ApplicationManager.getApplication().isHeadlessEnvironment) {
            context.indicator = EmptyProgressIndicator()
        } else {
            val indicatorResult = FutureResult<ProgressIndicator>()
            UIUtil.invokeLaterIfNeeded {
                object : Task.Backgroundable(context.project, context.taskName, true) {
                    override fun run(indicator: ProgressIndicator) {
                        indicatorResult.set(indicator)

                        var wasCanceled = false
                        while (!context.result.isDone) {
                            if (!wasCanceled && indicator.isCanceled) {
                                wasCanceled = true
                                synchronized(processCreationLock) {
                                    context.processHandler.destroyProcess()
                                }
                            }

                            try {
                                Thread.sleep(100)
                            } catch (e: InterruptedException) {
                                throw ProcessCanceledException(e)
                            }
                        }
                    }
                }.queue()
            }

            try {
                context.indicator = indicatorResult.get()
                context.indicator.text = context.progressTitle
                context.indicator.text2 = ""
            } catch (e: ExecutionException) {
                context.result.setException(e)
                return context.result
            }
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            if (!context.waitAndStart()) return@executeOnPooledThread

            TransactionGuard.submitTransaction(context.project, Runnable {
                synchronized(processCreationLock) {
                    if (context.indicator.isCanceled) {
                        context.canceled()
                        return@Runnable
                    }

                    MessageView.SERVICE.getInstance(context.project) // register ToolWindowId.MESSAGES_WINDOW
                    saveAllDocuments()
                    context.doExecute()
                }
            })
        }

        return context.result
    }

    fun isBuildConfiguration(configuration: CargoCommandConfiguration): Boolean {
        val args = ParametersListUtil.parse(configuration.command)
        return when (val command = args.firstOrNull()) {
            "build" -> true
            "test" -> {
                val additionalArguments = args.drop(1)
                val (commandArguments, _) = CargoArgsParser.parseArgs(command, additionalArguments)
                "--no-run" in commandArguments
            }
            else -> false
        }
    }

    fun getBuildConfiguration(configuration: CargoCommandConfiguration): CargoCommandConfiguration? {
        if (isBuildConfiguration(configuration)) return configuration

        val args = ParametersListUtil.parse(configuration.command)
        val command = args.firstOrNull() ?: return null
        if (command !in BUILDABLE_COMMANDS) return null
        val additionalArguments = args.drop(1)
        val (commandArguments, _) = CargoArgsParser.parseArgs(command, additionalArguments)

        // https://github.com/intellij-rust/intellij-rust/issues/3707
        if (command == "test" && commandArguments.contains("--doc")) return null

        val buildConfiguration = configuration.clone() as CargoCommandConfiguration
        buildConfiguration.name = "Build `${buildConfiguration.name}`"
        buildConfiguration.command = when (command) {
            "run" -> ParametersListUtil.join("build", *commandArguments.toTypedArray())
            "test" -> ParametersListUtil.join("test", "--no-run", *commandArguments.toTypedArray())
            else -> return null
        }
        return buildConfiguration
    }

    fun showBuildNotification(
        project: Project,
        messageType: MessageType,
        message: String,
        details: String? = null,
        time: Long = 0
    ) {
        val notificationContent = buildNotificationMessage(message, details, time)
        val notification = LOG_NOTIFICATION_GROUP.createNotification(notificationContent, messageType)
        notification.notify(project)

        if (messageType === MessageType.ERROR) {
            MessageView.SERVICE.getInstance(project) // register ToolWindowId.MESSAGES_WINDOW
            val manager = ToolWindowManager.getInstance(project)
            ApplicationManager.getApplication().invokeLater {
                manager.notifyByBalloon(ToolWindowId.MESSAGES_WINDOW, messageType, notificationContent)
            }
        }

        SystemNotifications.getInstance().notify(
            notification.groupId,
            StringUtil.capitalizeWords(message, true),
            details ?: ""
        )
    }

    private fun buildNotificationMessage(message: String, details: String?, time: Long): String {
        var notificationContent = message + if (details == null) "" else " with $details"
        if (time > 0) notificationContent += " in " + StringUtil.formatDuration(time, " ")
        return notificationContent
    }

    private val cargoBuildPatch: CargoPatch = { commandLine ->
        val additionalArguments = commandLine.additionalArguments.toMutableList()
        additionalArguments.remove("--quiet")
        addFormatJsonOption(additionalArguments, "--message-format")
        commandLine.copy(additionalArguments = additionalArguments)
    }
}

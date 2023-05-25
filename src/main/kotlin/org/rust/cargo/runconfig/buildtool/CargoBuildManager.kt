/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildContentManager
import com.intellij.build.BuildViewManager
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.SystemNotifications
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.text.SemVer
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.CargoCommandRunner
import org.rust.cargo.runconfig.CargoRunState
import org.rust.cargo.runconfig.addFormatJsonOption
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.ParsedCommand
import org.rust.cargo.runconfig.command.hasRemoteTarget
import org.rust.cargo.runconfig.target.localBuildArgsForRemoteRun
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.CargoArgsParser.Companion.parseArgs
import org.rust.cargo.util.parseSemVer
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.notifications.RsNotifications
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.isHeadlessEnvironment
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.saveAllDocuments
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

object CargoBuildManager {
    private val BUILDABLE_COMMANDS: List<String> = listOf("run", "test")

    private val CANCELED_BUILD_RESULT: Future<CargoBuildResult> =
        CompletableFuture.completedFuture(CargoBuildResult(succeeded = false, canceled = true, started = 0))

    private val MIN_RUSTC_VERSION: SemVer = "1.48.0".parseSemVer()

    val Project.isBuildToolWindowAvailable: Boolean
        get() {
            if (!isFeatureEnabled(RsExperiments.BUILD_TOOL_WINDOW)) return false
            val minVersion = cargoProjects.allProjects
                .mapNotNull { it.rustcInfo?.version?.semver }
                .minOrNull() ?: return false
            return minVersion >= MIN_RUSTC_VERSION
        }

    val CargoCommandConfiguration.isBuildToolWindowAvailable: Boolean
        get() {
            if (!project.isBuildToolWindowAvailable) return false
            val hasBuildBeforeRunTask = beforeRunTasks.any { task -> task is CargoBuildTaskProvider.BuildTask }
            return hasBuildBeforeRunTask && (!hasRemoteTarget || buildTarget.isLocal)
        }

    fun build(buildConfiguration: CargoBuildConfiguration): Future<CargoBuildResult> {
        val configuration = buildConfiguration.configuration
        val environment = buildConfiguration.environment
        val project = environment.project

        environment.cargoPatches += cargoBuildPatch
        val state = CargoRunState(
            environment,
            configuration,
            configuration.clean().ok ?: return CANCELED_BUILD_RESULT
        )

        val cargoProject = state.cargoProject ?: return CANCELED_BUILD_RESULT

        // Make sure build tool window is initialized:
        @Suppress("UsePropertyAccessSyntax")
        BuildContentManager.getInstance(project).getOrCreateToolWindow()

        if (isUnitTestMode) {
            lastBuildCommandLine = state.prepareCommandLine()
        }

        val buildId = Any()
        return execute(CargoBuildContext(
            cargoProject = cargoProject,
            environment = environment,
            taskName = "Build",
            progressTitle = "Building...",
            isTestBuild = state.commandLine.command == "test",
            buildId = buildId,
            parentId = buildId
        )) {
            val buildProgressListener = project.service<BuildViewManager>()
            if (!isHeadlessEnvironment) {
                @Suppress("UsePropertyAccessSyntax")
                val buildToolWindow = BuildContentManager.getInstance(project).getOrCreateToolWindow()
                buildToolWindow.setAvailable(true, null)
                if (environment.isActivateToolWindowBeforeRun) {
                    buildToolWindow.activate(null)
                }
            }

            processHandler = state.startProcess(processColors = false)
            processHandler?.addProcessListener(CargoBuildAdapter(this, buildProgressListener))
            processHandler?.startNotify()
        }
    }

    private fun execute(
        context: CargoBuildContext,
        doExecute: CargoBuildContext.() -> Unit
    ): CompletableFuture<CargoBuildResult> {
        context.environment.notifyProcessStartScheduled()
        val processCreationLock = Any()

        when {
            isUnitTestMode ->
                context.indicator = mockProgressIndicator ?: EmptyProgressIndicator()
            isHeadlessEnvironment ->
                context.indicator = EmptyProgressIndicator()
            else -> {
                val indicatorResult = CompletableFuture<ProgressIndicator>()
                UIUtil.invokeLaterIfNeeded {
                    object : Task.Backgroundable(context.project, context.taskName, true) {
                        override fun run(indicator: ProgressIndicator) {
                            indicatorResult.complete(indicator)

                            var wasCanceled = false
                            while (!context.result.isDone) {
                                if (!wasCanceled && indicator.isCanceled) {
                                    wasCanceled = true
                                    synchronized(processCreationLock) {
                                        context.processHandler?.destroyProcess()
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
                } catch (e: ExecutionException) {
                    context.result.completeExceptionally(e)
                    return context.result
                }
            }
        }

        context.indicator?.text = context.progressTitle
        context.indicator?.text2 = ""

        ApplicationManager.getApplication().executeOnPooledThread {
            if (!context.waitAndStart()) return@executeOnPooledThread
            context.environment.notifyProcessStarting()

            if (isUnitTestMode) {
                context.doExecute()
                return@executeOnPooledThread
            }

            // BACKCOMPAT: 2019.3
            @Suppress("DEPRECATION")
            TransactionGuard.submitTransaction(context.project, Runnable {
                synchronized(processCreationLock) {
                    val isCanceled = context.indicator?.isCanceled ?: false
                    if (isCanceled) {
                        context.canceled()
                        return@Runnable
                    }

                    saveAllDocuments()
                    context.doExecute()
                }
            })
        }

        return context.result
    }

    fun isBuildConfiguration(configuration: CargoCommandConfiguration): Boolean {
        val parsed = ParsedCommand.parse(configuration.command) ?: return false
        return when (val command = parsed.command) {
            "build", "check", "clippy" -> true
            "test" -> {
                val (commandArguments, _) = parseArgs(command, parsed.additionalArguments)
                "--no-run" in commandArguments
            }
            else -> false
        }
    }

    fun getBuildConfiguration(configuration: CargoCommandConfiguration): CargoCommandConfiguration? {
        if (isBuildConfiguration(configuration)) return configuration

        val parsed = ParsedCommand.parse(configuration.command) ?: return null
        if (parsed.command !in BUILDABLE_COMMANDS) return null
        val commandArguments = parseArgs(parsed.command, parsed.additionalArguments).commandArguments.toMutableList()
        commandArguments.addAll(configuration.localBuildArgsForRemoteRun)

        // https://github.com/intellij-rust/intellij-rust/issues/3707
        if (parsed.command == "test" && commandArguments.contains("--doc")) return null

        val buildConfiguration = configuration.clone() as CargoCommandConfiguration
        buildConfiguration.name = "Build `${buildConfiguration.name}`"
        buildConfiguration.command = ParametersListUtil.join(when (parsed.command) {
            "run" -> listOfNotNull(parsed.toolchain, "build", *commandArguments.toTypedArray())
            "test" -> listOfNotNull(parsed.toolchain, "test", "--no-run", *commandArguments.toTypedArray())
            else -> return null
        })

        buildConfiguration.emulateTerminal = false
        // building does not require root privileges and redirect input anyway
        buildConfiguration.withSudo = false
        buildConfiguration.isRedirectInput = false

        buildConfiguration.defaultTargetName = buildConfiguration.defaultTargetName
            .takeIf { buildConfiguration.buildTarget.isRemote }

        return buildConfiguration
    }

    fun createBuildEnvironment(
        buildConfiguration: CargoCommandConfiguration,
        environment: ExecutionEnvironment? = null
    ): ExecutionEnvironment? {
        require(isBuildConfiguration(buildConfiguration))
        val project = buildConfiguration.project
        val runManager = RunManager.getInstance(project) as? RunManagerImpl ?: return null
        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID) ?: return null
        val runner = ProgramRunner.findRunnerById(CargoCommandRunner.RUNNER_ID) ?: return null
        val settings = RunnerAndConfigurationSettingsImpl(runManager, buildConfiguration)
        settings.isActivateToolWindowBeforeRun = environment.isActivateToolWindowBeforeRun
        val buildEnvironment = ExecutionEnvironment(executor, runner, settings, project)
        environment?.copyUserDataTo(buildEnvironment)
        return buildEnvironment
    }

    fun showBuildNotification(
        project: Project,
        messageType: MessageType,
        message: String,
        details: String? = null,
        time: Long = 0
    ) {
        val notificationContent = buildNotificationMessage(message, details, time)
        val notification = RsNotifications.buildLogGroup().createNotification(notificationContent, messageType)
        notification.notify(project)

        if (messageType === MessageType.ERROR) {
            val manager = ToolWindowManager.getInstance(project)
            invokeLater {
                manager.notifyByBalloon(BuildContentManager.TOOL_WINDOW_ID, messageType, notificationContent)
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
        if (time > 0) notificationContent += " in " + NlsMessages.formatDuration(time)
        return notificationContent
    }

    private val cargoBuildPatch: CargoPatch = { commandLine ->
        val additionalArguments = mutableListOf<String>().apply {
            addAll(commandLine.additionalArguments)
            remove("-q")
            remove("--quiet")
            // If `json-diagnostic-rendered-ansi` is used, `rendered` field of JSON messages contains
            // embedded ANSI color codes for respecting rustc's default color scheme.
            addFormatJsonOption(this, "--message-format", "json-diagnostic-rendered-ansi")
        }

        val oldVariables = commandLine.environmentVariables
        val environmentVariables = EnvironmentVariablesData.create(
            // https://doc.rust-lang.org/cargo/reference/environment-variables.html#configuration-environment-variables
            // These environment variables are needed to force progress bar to non-TTY output
            oldVariables.envs + mapOf(
                "CARGO_TERM_PROGRESS_WHEN" to "always",
                "CARGO_TERM_PROGRESS_WIDTH" to "1000"
            ),
            oldVariables.isPassParentEnvs
        )

        commandLine.copy(additionalArguments = additionalArguments, environmentVariables = environmentVariables)
    }

    @TestOnly
    @Volatile
    var mockProgressIndicator: MockProgressIndicator? = null

    @TestOnly
    @Volatile
    var lastBuildCommandLine: CargoCommandLine? = null
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */


@file:Suppress("UnstableApiUsage")

package org.rust.cargo.runconfig.buildtool

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.task.*
import com.intellij.task.impl.ProjectModelBuildTaskImpl
import org.jetbrains.concurrency.*
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.runconfig.CargoCommandRunner
import org.rust.cargo.runconfig.buildProject
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.createBuildEnvironment
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowAvailable
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.createCargoCommandRunConfiguration
import org.rust.cargo.runconfig.hasRemoteTarget
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.tools.cargo
import org.rust.cargo.util.cargoProjectRoot
import org.rust.ide.notifications.confirmLoadingUntrustedProject
import org.rust.openapiext.isHeadlessEnvironment
import org.rust.stdext.buildList
import java.util.concurrent.*

private val LOG: Logger = logger<CargoBuildTaskRunner>()

class CargoBuildTaskRunner : ProjectTaskRunner() {

    override fun run(
        project: Project,
        context: ProjectTaskContext,
        vararg tasks: ProjectTask
    ): Promise<Result> {
        if (project.isDisposed) {
            return rejectedPromise("Project is already disposed")
        }

        val confirmed = invokeAndWaitIfNeeded { project.confirmLoadingUntrustedProject() }
        if (!confirmed) {
            return rejectedPromise(RsBundle.message("untrusted.project.notification.execution.error"))
        }

        val configuration = context.runConfiguration as? CargoCommandConfiguration
        if (configuration?.hasRemoteTarget == true || !project.isBuildToolWindowAvailable) {
            invokeLater { project.buildProject() }
            return rejectedPromise()
        }

        val resultPromise = AsyncPromise<Result>()

        val waitingIndicator = CompletableFuture<ProgressIndicator>()
        val queuedTask = BackgroundableProjectTaskRunner(
            project,
            tasks,
            this,
            resultPromise,
            waitingIndicator
        )

        if (!isHeadlessEnvironment) {
            WaitingTask(project, waitingIndicator, queuedTask.executionStarted).queue()
        }

        CargoBuildSessionsQueueManager.getInstance(project)
            .buildSessionsQueue
            .run(queuedTask, ModalityState.defaultModalityState(), EmptyProgressIndicator())

        return resultPromise
    }

    override fun canRun(projectTask: ProjectTask): Boolean =
        when (projectTask) {
            is ModuleBuildTask ->
                projectTask.module.cargoProjectRoot != null
            is ProjectModelBuildTask<*> -> {
                val buildableElement = projectTask.buildableElement
                buildableElement is CargoBuildConfiguration && buildableElement.enabled
            }
            else -> false
        }

    fun expandTask(task: ProjectTask): List<ProjectTask> {
        if (task !is ModuleBuildTask) return listOf(task)

        val project = task.module.project
        val runManager = RunManager.getInstance(project)

        val selectedConfiguration = runManager.selectedConfiguration?.configuration as? CargoCommandConfiguration
        if (selectedConfiguration != null) {
            val buildConfiguration = getBuildConfiguration(selectedConfiguration) ?: return emptyList()
            val environment = createBuildEnvironment(buildConfiguration) ?: return emptyList()
            val buildableElement = CargoBuildConfiguration(buildConfiguration, environment)
            return listOf(ProjectModelBuildTaskImpl(buildableElement, false))
        }

        val cargoProjects = project.cargoProjects.allProjects
        if (cargoProjects.isEmpty()) return emptyList()

        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
            ?: return emptyList()
        val runner = ProgramRunner.findRunnerById(CargoCommandRunner.RUNNER_ID) ?: return emptyList()

        val additionalArguments = buildList<String> {
            val settings = project.rustSettings
            add("--all")
            if (settings.compileAllTargets) {
                val allTargets = settings.toolchain
                    ?.cargo()
                    ?.checkSupportForBuildCheckAllTargets()
                    ?: false
                if (allTargets) add("--all-targets")
            }
        }

        return cargoProjects.mapNotNull { cargoProject ->
            val commandLine = CargoCommandLine.forProject(cargoProject, "build", additionalArguments)
            val settings = runManager.createCargoCommandRunConfiguration(commandLine)
            val environment = ExecutionEnvironment(executor, runner, settings, project)
            val configuration = settings.configuration as? CargoCommandConfiguration ?: return@mapNotNull null
            configuration.emulateTerminal = false
            val buildableElement = CargoBuildConfiguration(configuration, environment)
            ProjectModelBuildTaskImpl(buildableElement, false)
        }
    }

    fun executeTask(task: ProjectTask): Promise<Result> {
        if (task !is ProjectModelBuildTask<*>) {
            return resolvedPromise(TaskRunnerResults.ABORTED)
        }

        val result = try {
            val buildFuture = CargoBuildManager.build(task.buildableElement as CargoBuildConfiguration)
            val buildResult = buildFuture.get()
            when {
                buildResult.canceled -> TaskRunnerResults.ABORTED
                buildResult.succeeded -> TaskRunnerResults.SUCCESS
                else -> TaskRunnerResults.FAILURE
            }
        } catch (e: ExecutionException) {
            TaskRunnerResults.FAILURE
        }

        val promise = AsyncPromise<Result>()
        promise.setResult(result)
        return promise
    }
}

private class BackgroundableProjectTaskRunner(
    project: Project,
    private val tasks: Array<out ProjectTask>,
    private val parentRunner: CargoBuildTaskRunner,
    private val totalPromise: AsyncPromise<ProjectTaskRunner.Result>,
    private val waitingIndicator: Future<ProgressIndicator>
) : Task.Backgroundable(project, "Building...", true) {
    val executionStarted: CompletableFuture<Boolean> = CompletableFuture()

    override fun run(indicator: ProgressIndicator) {
        if (!waitForStart()) {
            if (totalPromise.state == Promise.State.PENDING) {
                totalPromise.cancel()
            }
            return
        }

        val allTasks = collectTasks(tasks)
        if (allTasks.isEmpty()) {
            totalPromise.setResult(TaskRunnerResults.FAILURE)
            return
        }

        try {
            for (task in allTasks) {
                val promise = runTask(task)
                if (promise.blockingGet(Integer.MAX_VALUE) != TaskRunnerResults.SUCCESS) {
                    // Do not continue session if one of builds failed
                    totalPromise.setResult(TaskRunnerResults.FAILURE)
                    break
                }
            }

            // everything succeeded - set final result to success
            if (totalPromise.isPending) {
                totalPromise.setResult(TaskRunnerResults.SUCCESS)
            }
        } catch (e: InterruptedException) {
            totalPromise.setResult(TaskRunnerResults.ABORTED)
            throw ProcessCanceledException(e)
        } catch (e: CancellationException) {
            totalPromise.setResult(TaskRunnerResults.ABORTED)
            throw ProcessCanceledException(e)
        } catch (e: Throwable) {
            LOG.error(e)
            totalPromise.setResult(TaskRunnerResults.FAILURE)
        }
    }

    private fun waitForStart(): Boolean {
        if (isHeadlessEnvironment) return true

        try {
            // Check if this build wasn't cancelled while it was in queue through waiting indicator
            val cancelled = waitingIndicator.get().isCanceled
            // Notify waiting background task that this build started and there is no more need for this indicator
            executionStarted.complete(true)
            return !cancelled
        } catch (e: InterruptedException) {
            totalPromise.setResult(TaskRunnerResults.ABORTED)
            throw ProcessCanceledException(e)
        } catch (e: CancellationException) {
            totalPromise.setResult(TaskRunnerResults.ABORTED)
            throw ProcessCanceledException(e)
        } catch (e: Throwable) {
            LOG.error(e)
            totalPromise.setResult(TaskRunnerResults.FAILURE)
            throw ProcessCanceledException(e)
        }
    }

    private fun collectTasks(tasks: Array<out ProjectTask>): Collection<ProjectTask> {
        val expandedTasks = tasks.filter { parentRunner.canRun(it) }.map { parentRunner.expandTask(it) }
        return if (expandedTasks.any { it.isEmpty() }) emptyList() else expandedTasks.flatten()
    }

    private fun runTask(task: ProjectTask): Promise<ProjectTaskRunner.Result> = parentRunner.executeTask(task)
}

private class WaitingTask(
    project: Project,
    val waitingIndicator: CompletableFuture<ProgressIndicator>,
    val executionStarted: Future<Boolean>
) : Task.Backgroundable(project, "Waiting for the current build to finish...", true) {
    override fun run(indicator: ProgressIndicator) {
        // Wait until queued task will start executing.
        // Needed so that user can cancel build tasks from queue.
        waitingIndicator.complete(indicator)
        try {
            while (true) {
                indicator.checkCanceled()
                try {
                    executionStarted.get(100, TimeUnit.MILLISECONDS)
                    break
                } catch (ignore: TimeoutException) {
                }
            }
        } catch (e: CancellationException) {
            throw ProcessCanceledException(e)
        } catch (e: InterruptedException) {
            throw ProcessCanceledException(e)
        } catch (e: ExecutionException) {
            LOG.error(e)
            throw ProcessCanceledException(e)
        }
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */


// BACKCOMPAT: 2019.2
@file:Suppress("DEPRECATION", "UnstableApiUsage")

package org.rust.cargo.runconfig.buildtool

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isUnitTestMode
import com.intellij.task.*
import com.intellij.task.impl.ProjectModelBuildTaskImpl
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.runconfig.CargoCommandRunner
import org.rust.cargo.runconfig.buildProject
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.CANCELED_BUILD_RESULT
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.createBuildEnvironment
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.createCargoCommandRunConfiguration
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.cargoProjectRoot
import org.rust.stdext.buildList
import java.util.concurrent.*

private val LOG: Logger = Logger.getInstance(CargoBuildTaskRunner::class.java)

class CargoBuildTaskRunner : ProjectTaskRunner() {
    private val buildSessionsQueue: BackgroundTaskQueue = BackgroundTaskQueue(null, "Building...")

    override fun run(
        project: Project,
        context: ProjectTaskContext,
        callback: ProjectTaskNotification?,
        tasks: Collection<ProjectTask>
    ) {
        if (project.isDisposed) return

        if (!project.isBuildToolWindowEnabled) {
            invokeLater { project.buildProject() }
            return
        }

        val waitingIndicator = CompletableFuture<ProgressIndicator>()
        val queuedTask = BackgroundableProjectTaskRunner(
            project,
            tasks,
            this,
            callback,
            waitingIndicator
        )

        if (isUnitTestMode) {
            waitingIndicator.complete(EmptyProgressIndicator())
        } else {
            WaitingTask(project, waitingIndicator, queuedTask.executionStarted).queue()
        }

        buildSessionsQueue.run(queuedTask, null, EmptyProgressIndicator())
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
                    ?.rawCargo()
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
            val buildableElement = CargoBuildConfiguration(configuration, environment)
            ProjectModelBuildTaskImpl(buildableElement, false)
        }
    }

    fun executeTask(task: ProjectTask, callback: ProjectTaskNotification?) {
        val result = if (task is ProjectModelBuildTask<*>) {
            CargoBuildManager.build(task.buildableElement as CargoBuildConfiguration)
        } else {
            CANCELED_BUILD_RESULT
        }

        if (callback != null) {
            try {
                val buildResult = result.get()
                callback.finished(ProjectTaskResult(
                    buildResult.canceled,
                    if (buildResult.succeeded) 0 else Integer.max(1, buildResult.errors),
                    buildResult.warnings
                ))
            } catch (e: ExecutionException) {
                callback.finished(ProjectTaskResult(false, 1, 0))
            }
        }
    }
}

private class BackgroundableProjectTaskRunner(
    project: Project,
    private val tasks: Collection<ProjectTask>,
    private val parentRunner: CargoBuildTaskRunner,
    private val callback: ProjectTaskNotification?,
    private val waitingIndicator: Future<ProgressIndicator>
) : Task.Backgroundable(project, "Building...", true) {
    val executionStarted: CompletableFuture<Boolean> = CompletableFuture()

    override fun run(indicator: ProgressIndicator) {
        if (!waitForStart()) return

        val allTasks = collectTasks(tasks)
        if (allTasks.isEmpty()) {
            callback?.finished(ProjectTaskResult(false, 1, 0))
            return
        }

        val compositeCallback = callback?.let { CompositeCallback(it, allTasks.size) }
        try {
            for (task in allTasks) {
                val future = runTask(task, compositeCallback)
                if (!future.get()) {
                    // Do not continue session if one of builds failed
                    compositeCallback?.forceFinished(ProjectTaskResult(false, 1, 0))
                    break
                }
            }
        } catch (e: InterruptedException) {
            compositeCallback?.forceFinished(ProjectTaskResult(true, 0, 0))
            throw ProcessCanceledException(e)
        } catch (e: CancellationException) {
            compositeCallback?.forceFinished(ProjectTaskResult(true, 0, 0))
            throw ProcessCanceledException(e)
        } catch (e: Throwable) {
            LOG.error(e)
            compositeCallback?.forceFinished(ProjectTaskResult(false, 1, 0))
        }
    }

    private fun waitForStart(): Boolean {
        try {
            // Check if this build wasn't cancelled while it was in queue through waiting indicator
            val cancelled = waitingIndicator.get().isCanceled
            // Notify waiting background task that this build started and there is no more need for this indicator
            executionStarted.complete(true)
            return !cancelled
        } catch (e: InterruptedException) {
            callback?.finished(ProjectTaskResult(true, 0, 0))
            throw ProcessCanceledException(e)
        } catch (e: CancellationException) {
            callback?.finished(ProjectTaskResult(true, 0, 0))
            throw ProcessCanceledException(e)
        } catch (e: Throwable) {
            LOG.error(e)
            callback?.finished(ProjectTaskResult(true, 1, 0))
            throw ProcessCanceledException(e)
        }
    }

    private fun collectTasks(tasks: Collection<ProjectTask>): Collection<ProjectTask> {
        val expandedTasks = tasks.filter { parentRunner.canRun(it) }.map { parentRunner.expandTask(it) }
        return if (expandedTasks.any { it.isEmpty() }) emptyList() else expandedTasks.flatten()
    }

    private fun runTask(task: ProjectTask, callback: ProjectTaskNotification?): Future<Boolean> {
        val future = CompletableFuture<Boolean>()
        parentRunner.executeTask(task, FutureCallback(callback, future))
        return future
    }
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

private class FutureCallback(
    val basicCallback: ProjectTaskNotification?,
    val future: CompletableFuture<Boolean>
) : ProjectTaskNotification {
    override fun finished(executionResult: ProjectTaskResult) {
        basicCallback?.finished(executionResult)
        if (executionResult.isAborted) {
            future.cancel(false)
        } else {
            future.complete(executionResult.errors == 0)
        }
    }
}

private class CompositeCallback(
    val basicCallback: ProjectTaskNotification,
    val total: Int
) : ProjectTaskNotification {
    private var isAborted: Boolean = false
    private var errors: Int = 0
    private var warnings: Int = 0
    private var currentIdx: Int = 0

    @Synchronized
    override fun finished(executionResult: ProjectTaskResult) {
        isAborted = isAborted || executionResult.isAborted
        errors += executionResult.errors
        warnings += executionResult.warnings
        currentIdx++
        if (currentIdx >= total) {
            basicCallback.finished(ProjectTaskResult(isAborted, errors, warnings))
        }
    }

    @Synchronized
    fun forceFinished(executionResult: ProjectTaskResult) {
        if (currentIdx < total) {
            currentIdx = total
            isAborted = isAborted || executionResult.isAborted
            errors += executionResult.errors
            warnings += executionResult.warnings
            basicCallback.finished(executionResult)
        }
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildContentDescriptor
import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.impl.*
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.StopProcessAction
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import org.rust.RsBundle
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.runconfig.createFilters
import javax.swing.JComponent

@Suppress("UnstableApiUsage")
class CargoBuildAdapter(
    private val context: CargoBuildContext,
    buildProgressListener: BuildProgressListener
) : CargoBuildAdapterBase(context, buildProgressListener) {

    init {
        val processHandler = checkNotNull(context.processHandler) { "Process handler can't be null" }
        context.environment.notifyProcessStarted(processHandler)

        val buildContentDescriptor = BuildContentDescriptor(null, null, object : JComponent() {}, RsBundle.message("build"))
        val activateToolWindow = context.environment.isActivateToolWindowBeforeRun
        buildContentDescriptor.isActivateToolWindowWhenAdded = activateToolWindow
        buildContentDescriptor.isActivateToolWindowWhenFailed = activateToolWindow
        buildContentDescriptor.isNavigateToError = context.project.rustSettings.autoShowErrorsInEditor

        val descriptor = DefaultBuildDescriptor(context.buildId, RsBundle.message("build.event.title.run.cargo.command"), context.workingDirectory.toString(), context.started)
            .withContentDescriptor { buildContentDescriptor }
            .withRestartAction(createRerunAction(processHandler, context.environment))
            .withRestartAction(createStopAction(processHandler))
            .apply { createFilters(context.cargoProject).forEach { withExecutionFilter(it) } }

        val buildStarted = StartBuildEventImpl(descriptor, RsBundle.message("build.event.message.running", context.taskName))
        buildProgressListener.onEvent(context.buildId, buildStarted)
    }

    override fun onBuildOutputReaderFinish(
        event: ProcessEvent,
        isSuccess: Boolean,
        isCanceled: Boolean,
        error: Throwable?
    ) {
        val (status, result) = when {
            isCanceled -> "canceled" to SkippedResultImpl()
            isSuccess -> "successful" to SuccessResultImpl()
            else -> "failed" to FailureResultImpl(error)
        }

        val buildFinished = FinishBuildEventImpl(
            context.buildId,
            null,
            System.currentTimeMillis(),
            RsBundle.message("build.event.message.", context.taskName, status),
            result
        )
        buildProgressListener.onEvent(context.buildId, buildFinished)
        context.finished(isSuccess)

        context.environment.notifyProcessTerminated(event.processHandler, event.exitCode)

        val targetPath = context.workingDirectory.resolve(CargoConstants.ProjectLayout.target)
        val targetDir = VfsUtil.findFile(targetPath, true) ?: return
        VfsUtil.markDirtyAndRefresh(true, true, true, targetDir)
    }

    override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
        context.environment.notifyProcessTerminating(event.processHandler)
    }

    companion object {

        private fun createStopAction(processHandler: ProcessHandler): StopProcessAction =
            StopProcessAction("Stop", "Stop", processHandler)

        private fun createRerunAction(processHandler: ProcessHandler, environment: ExecutionEnvironment): RestartProcessAction =
            RestartProcessAction(processHandler, environment)

        private class RestartProcessAction(
            private val processHandler: ProcessHandler,
            private val environment: ExecutionEnvironment
        ) : DumbAwareAction() {
            private val isEnabled: Boolean
                get() {
                    val project = environment.project
                    val settings = environment.runnerAndConfigurationSettings
                    return (!DumbService.isDumb(project) || settings == null || settings.type.isDumbAware) &&
                        !ExecutorRegistry.getInstance().isStarting(environment) &&
                        !processHandler.isProcessTerminating
                }

            override fun update(event: AnActionEvent) {
                val presentation = event.presentation
                presentation.text = RsBundle.message("action.rerun.text", StringUtil.escapeMnemonics(environment.runProfile.name))
                presentation.icon = if (processHandler.isProcessTerminated) AllIcons.Actions.Compile else AllIcons.Actions.Restart
                presentation.isEnabled = isEnabled
            }

            override fun actionPerformed(event: AnActionEvent) {
                ExecutionManagerImpl.stopProcess(processHandler)
                ExecutionUtil.restart(environment)
            }
        }
    }
}

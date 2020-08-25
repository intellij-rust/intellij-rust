/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.impl.*
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.StopProcessAction
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.runconfig.RsAnsiEscapeDecoder
import org.rust.cargo.runconfig.RsExecutableRunner.Companion.binaries
import org.rust.cargo.runconfig.createFilters

@Suppress("UnstableApiUsage")
class CargoBuildAdapter(
    private val context: CargoBuildContext,
    private val buildProgressListener: BuildProgressListener
) : ProcessAdapter(), AnsiEscapeDecoder.ColoredTextAcceptor {
    private val decoder: AnsiEscapeDecoder = RsAnsiEscapeDecoder()

    private val buildOutputParser: CargoBuildEventsConverter = CargoBuildEventsConverter(context)

    private val instantReader = BuildOutputInstantReaderImpl(
        context.buildId,
        context.buildId,
        buildProgressListener,
        listOf(buildOutputParser)
    )

    private val textBuffer: MutableList<String> = mutableListOf()

    init {
        val processHandler = checkNotNull(context.processHandler) { "Process handler can't be null" }
        context.environment.notifyProcessStarted(processHandler)
        val descriptor = DefaultBuildDescriptor(
            context.buildId,
            "Run Cargo command",
            context.workingDirectory.toString(),
            context.started
        )
        val buildStarted = StartBuildEventImpl(descriptor, "${context.taskName} running...")
            .withExecutionFilters(*createFilters(context.cargoProject).toTypedArray())
            .withRestartAction(createRerunAction(processHandler, context.environment))
            .withRestartAction(createStopAction(processHandler))
        buildProgressListener.onEvent(context.buildId, buildStarted)
    }

    override fun processTerminated(event: ProcessEvent) {
        instantReader.closeAndGetFuture().whenComplete { _, error ->
            val isSuccess = event.exitCode == 0 && context.errors == 0
            val isCanceled = context.indicator?.isCanceled ?: false
            context.environment.binaries = buildOutputParser.binaries.takeIf { isSuccess && !isCanceled }

            val (status, result) = when {
                isCanceled -> "canceled" to SkippedResultImpl()
                isSuccess -> "successful" to SuccessResultImpl()
                else -> "failed" to FailureResultImpl(error)
            }
            val buildFinished = FinishBuildEventImpl(
                context.buildId,
                null,
                System.currentTimeMillis(),
                "${context.taskName} $status",
                result
            )
            buildProgressListener.onEvent(context.buildId, buildFinished)
            context.finished(isSuccess)

            context.environment.notifyProcessTerminated(event.processHandler, event.exitCode)

            val targetPath = context.workingDirectory.resolve(CargoConstants.ProjectLayout.target)
            val targetDir = VfsUtil.findFile(targetPath, true) ?: return@whenComplete
            VfsUtil.markDirtyAndRefresh(true, true, true, targetDir)
        }
    }

    override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
        context.environment.notifyProcessTerminating(event.processHandler)
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        var rawText = event.text
        if (SystemInfo.isWindows && rawText.matches(BUILD_PROGRESS_INNER_RE)) {
            rawText += "\n"
        }

        textBuffer.add(rawText)
        if (!rawText.endsWith("\n")) return

        val concat = textBuffer.joinToString("")

        // If the line contains a JSON message (contains `{"reason"` substring), then it should end with `}\n`,
        // otherwise the line contains only part of the message.
        if (concat.contains("{\"reason\"") && !concat.endsWith("}\n")) return

        val text = concat.replace(BUILD_PROGRESS_FULL_RE) { it.value.trimEnd(' ', '\r', '\n') + "\n" }
        textBuffer.clear()

        decoder.escapeText(text, outputType, this)
        buildOutputParser.parseOutput(
            text.replace(BUILD_PROGRESS_FULL_RE, ""),
            outputType == ProcessOutputTypes.STDOUT
        ) {
            buildProgressListener.onEvent(context.buildId, it)
        }
    }

    override fun coloredTextAvailable(text: String, outputType: Key<*>) {
        instantReader.append(text)
    }

    companion object {
        private val BUILD_PROGRESS_INNER_RE: Regex = """ \[ *=*>? *] \d+/\d+: [\w\-(.)]+(, [\w\-(.)]+)*""".toRegex()
        private val BUILD_PROGRESS_FULL_RE: Regex = """ *Building$BUILD_PROGRESS_INNER_RE( *[\r\n])*""".toRegex()

        private fun createStopAction(processHandler: ProcessHandler): StopProcessAction =
            StopProcessAction("Stop", "Stop", processHandler)

        private fun createRerunAction(processHandler: ProcessHandler, environment: ExecutionEnvironment): RestartProcessAction =
            RestartProcessAction(processHandler, environment)

        private class RestartProcessAction(
            private val processHandler: ProcessHandler,
            private val environment: ExecutionEnvironment
        ) : DumbAwareAction(), AnAction.TransparentUpdate {
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
                presentation.text = "Rerun '${StringUtil.escapeMnemonics(environment.runProfile.name)}'"
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

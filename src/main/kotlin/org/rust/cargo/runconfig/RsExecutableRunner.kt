/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.Cargo.Companion.cargoCommonPatch
import org.rust.cargo.util.CargoArgsParser.Companion.parseArgs
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

abstract class RsExecutableRunner(
    private val executorId: String,
    private val errorMessageTitle: String
) : RsDefaultProgramRunnerBase() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != this.executorId || profile !is CargoCommandConfiguration ||
            profile.clean() !is CargoCommandConfiguration.CleanConfiguration.Ok) return false
        return profile.project.isBuildToolWindowEnabled &&
            !isBuildConfiguration(profile) &&
            getBuildConfiguration(profile) != null
    }

    override fun execute(
        environment: ExecutionEnvironment,
        callback: ProgramRunner.Callback?,
        state: RunProfileState
    ) {
        if (!checkToolchainSupported(environment.project, state as CargoRunStateBase)) return
        if (!checkToolchainConfigured(environment.project)) return
        state.addCommandLinePatch(cargoCommonPatch)
        environment.putUserData(BINARIES, CompletableFuture())
        super.execute(environment, callback, state)
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (state !is CargoRunStateBase) return null

        val binaries = environment.binaries.orEmpty()
        val errorMessage = when {
            binaries.isEmpty() -> "Can't find a binary."
            binaries.size > 1 -> "More than one binary was produced. " +
                "Please specify `--bin`, `--lib`, `--test` or `--example` flag explicitly."
            else -> null
        }
        if (errorMessage != null) {
            environment.project.showErrorDialog(errorMessage)
            return null
        }

        val runCargoCommand = state.prepareCommandLine()
        val (_, executableArguments) = parseArgs(runCargoCommand.command, runCargoCommand.additionalArguments)
        val runExecutable = Cargo.createGeneralCommandLine(
            binaries.single(),
            runCargoCommand.workingDirectory,
            runCargoCommand.backtraceMode,
            runCargoCommand.environmentVariables,
            executableArguments,
            runCargoCommand.emulateTerminal
        )
        return showRunContent(state, environment, runExecutable)
    }

    protected open fun showRunContent(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runExecutable: GeneralCommandLine
    ): RunContentDescriptor? = showRunContent(executeCommandLine(state, runExecutable, environment), environment)

    private fun executeCommandLine(
        state: CargoRunStateBase,
        commandLine: GeneralCommandLine,
        environment: ExecutionEnvironment
    ): DefaultExecutionResult {
        val runConfiguration = state.runConfiguration
        val context = ConfigurationExtensionContext()

        val extensionManager = RsRunConfigurationExtensionManager.getInstance()
        extensionManager.patchCommandLine(runConfiguration, environment, commandLine, context)
        extensionManager.patchCommandLineState(runConfiguration, environment, state, context)

        val handler = RsKillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination

        extensionManager.attachExtensionsToProcess(runConfiguration, handler, environment, context)

        val console = state.consoleBuilder.console
        console.attachToProcess(handler)
        return DefaultExecutionResult(console, handler)
    }

    open fun checkToolchainSupported(project: Project, state: CargoRunStateBase): Boolean = true

    open fun checkToolchainConfigured(project: Project): Boolean = true

    protected fun Project.showErrorDialog(message: String) {
        Messages.showErrorDialog(this, message, errorMessageTitle)
    }

    companion object {
        private val BINARIES: Key<CompletableFuture<List<Path>>> = Key.create("CARGO.CONFIGURATION.BINARIES")

        var ExecutionEnvironment.binaries: List<Path>?
            get() = getUserData(this@Companion.BINARIES)?.get()
            set(value) {
                getUserData(this@Companion.BINARIES)?.complete(value)
            }
    }
}

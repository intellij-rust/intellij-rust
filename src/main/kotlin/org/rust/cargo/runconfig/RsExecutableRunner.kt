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
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.buildtool.cargoPatches
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.tools.Cargo.Companion.getCargoCommonPatch
import org.rust.cargo.util.CargoArgsParser.Companion.parseArgs
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.toPath
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

    override fun execute(environment: ExecutionEnvironment) {
        val state = environment.state as CargoRunStateBase
        val project = environment.project
        val host = project.computeWithCancelableProgress("Checking if toolchain is supported...") {
            state.rustVersion()?.host.orEmpty()
        }
        if (!checkToolchainConfigured(project)) return
        val toolchainError = checkToolchainSupported(host)
        if (toolchainError != null) {
            processInvalidToolchain(project, toolchainError)
            return
        }
        environment.cargoPatches += getCargoCommonPatch(project)
        environment.putUserData(ARTIFACT, CompletableFuture())
        super.execute(environment)
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (state !is CargoRunStateBase) return null

        val artifact = environment.artifact
        val binaries = artifact?.executables.orEmpty()
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
        val workingDirectory = getWorkingDirectory(environment.project, runCargoCommand, artifact)
        val (_, executableArguments) = parseArgs(runCargoCommand.command, runCargoCommand.additionalArguments)
        val runExecutable = state.toolchain.createGeneralCommandLine(
            binaries.single().toPath(),
            workingDirectory,
            runCargoCommand.redirectInputFrom,
            runCargoCommand.backtraceMode,
            runCargoCommand.environmentVariables,
            executableArguments,
            runCargoCommand.emulateTerminal,
            patchToRemote = false
        )
        return showRunContent(state, environment, runExecutable)
    }

    private fun getWorkingDirectory(
        project: Project,
        runCargoCommand: CargoCommandLine,
        artifact: CargoMetadata.Artifact?
    ): Path {
        if (runCargoCommand.command != "test") return runCargoCommand.workingDirectory
        val packageId = artifact?.package_id ?: return runCargoCommand.workingDirectory
        val pkg = project.cargoProjects.allProjects
            .mapNotNull { it.workspace?.findPackageById(packageId) }
            .firstOrNull()
        return pkg?.rootDirectory ?: runCargoCommand.workingDirectory
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

        val handler = state.toolchain.startProcess(commandLine)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination

        extensionManager.attachExtensionsToProcess(runConfiguration, handler, environment, context)

        val console = state.consoleBuilder.console
        console.attachToProcess(handler)
        return DefaultExecutionResult(console, handler)
    }

    open fun checkToolchainSupported(host: String): BuildResult.ToolchainError? = null

    open fun checkToolchainConfigured(project: Project): Boolean = true

    open fun processInvalidToolchain(project: Project, toolchainError: BuildResult.ToolchainError) {
        project.showErrorDialog(toolchainError.message)
    }

    private fun Project.showErrorDialog(message: String) {
        Messages.showErrorDialog(this, message, errorMessageTitle)
    }

    companion object {
        private val ARTIFACT: Key<CompletableFuture<CargoMetadata.Artifact>> =
            Key.create("CARGO.CONFIGURATION.ARTIFACT")

        var ExecutionEnvironment.artifact: CargoMetadata.Artifact?
            get() = getUserData(this@Companion.ARTIFACT)?.get()
            set(value) {
                getUserData(this@Companion.ARTIFACT)?.complete(value)
            }
    }
}

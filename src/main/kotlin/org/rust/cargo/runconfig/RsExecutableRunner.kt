/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DialogMessage
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.buildtool.cargoPatches
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.impl.CompilerArtifactMessage
import org.rust.cargo.toolchain.tools.Cargo.Companion.getCargoCommonPatch
import org.rust.cargo.util.CargoArgsParser.Companion.parseArgs
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.toPath
import java.util.concurrent.CompletableFuture

abstract class RsExecutableRunner(
    private val executorId: String,
    private val errorMessageTitle: String
) : RsDefaultProgramRunnerBase() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != this.executorId || profile !is CargoCommandConfiguration ||
            profile.clean() !is CargoCommandConfiguration.CleanConfiguration.Ok) return false
        return profile.isBuildToolWindowEnabled &&
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
        val toolchainError = checkToolchainSupported(project, host)
        if (toolchainError != null) {
            processInvalidToolchain(project, toolchainError)
            return
        }
        environment.cargoPatches += getCargoCommonPatch(project)
        environment.putUserData(ARTIFACTS, CompletableFuture())
        super.execute(environment)
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (state !is CargoRunStateBase) return null

        val artifacts = environment.artifacts.orEmpty()
        val artifact = artifacts.firstOrNull()
        val binaries = artifact?.executables.orEmpty()

        @Suppress("UnstableApiUsage")
        @DialogMessage
        fun checkErrors(items: List<Any>, itemName: String): String? = when {
            items.isEmpty() -> "Can't find a $itemName."
            items.size > 1 -> "More than one $itemName was produced. " +
                "Please specify `--bin`, `--lib`, `--test` or `--example` flag explicitly."
            else -> null
        }

        val errorMessage = checkErrors(artifacts, "artifact") ?: checkErrors(binaries, "binary")
        if (errorMessage != null) {
            environment.project.showErrorDialog(errorMessage)
            return null
        }

        val pkg = artifact?.package_id?.let { id ->
            environment.project.cargoProjects.allProjects
                .mapNotNull { it.workspace?.findPackageById(id) }
                .firstOrNull { it.origin == PackageOrigin.WORKSPACE }
        }

        val runCargoCommand = state.prepareCommandLine()
        val workingDirectory = pkg?.rootDirectory
            ?.takeIf { runCargoCommand.command == "test" }
            ?: runCargoCommand.workingDirectory
        val environmentVariables = runCargoCommand.environmentVariables.run { with(envs + pkg?.env.orEmpty()) }
        val (_, executableArguments) = parseArgs(runCargoCommand.command, runCargoCommand.additionalArguments)
        val runExecutable = state.toolchain.createGeneralCommandLine(
            binaries.single().toPath(),
            workingDirectory,
            runCargoCommand.redirectInputFrom,
            runCargoCommand.backtraceMode,
            environmentVariables,
            executableArguments,
            runCargoCommand.emulateTerminal,
            runCargoCommand.withSudo,
            patchToRemote = false // patching is performed for debugger/profiler/valgrind on CLion side if needed
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
    ): DefaultExecutionResult = state.executeCommandLine(commandLine, environment)

    open fun checkToolchainSupported(project: Project, host: String): BuildResult.ToolchainError? = null

    open fun checkToolchainConfigured(project: Project): Boolean = true

    open fun processInvalidToolchain(project: Project, toolchainError: BuildResult.ToolchainError) {
        project.showErrorDialog(toolchainError.message)
    }

    private fun Project.showErrorDialog(@Suppress("UnstableApiUsage") @DialogMessage message: String) {
        Messages.showErrorDialog(this, message, errorMessageTitle)
    }

    companion object {
        private val ARTIFACTS: Key<CompletableFuture<List<CompilerArtifactMessage>>> =
            Key.create("CARGO.CONFIGURATION.ARTIFACTS")

        var ExecutionEnvironment.artifacts: List<CompilerArtifactMessage>?
            get() = getUserData(this@Companion.ARTIFACTS)?.get()
            set(value) {
                getUserData(this@Companion.ARTIFACTS)?.complete(value)
            }
    }
}

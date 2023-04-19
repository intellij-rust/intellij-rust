/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.customBuild

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import org.rust.RsBundle
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.RsExecutableRunner
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowAvailable
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.impl.CompilerArtifactMessage
import org.rust.openapiext.pathAsPath
import org.rust.stdext.toPath
import java.nio.file.Path

private val ERROR_MESSAGE_TITLE = RsBundle.message("run.config.rust.custom.build.runner.error.title")

open class CustomBuildRunner(
    executorId: String = DefaultRunExecutor.EXECUTOR_ID,
    errorMessageTitle: String = ERROR_MESSAGE_TITLE
) : RsExecutableRunner(executorId, errorMessageTitle) {
    override fun getRunnerId() = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != this.executorId ||
            profile !is CustomBuildConfiguration ||
            profile.clean() !is CargoCommandConfiguration.CleanConfiguration.Ok) return false
        return profile.isBuildToolWindowAvailable
    }

    // See https://doc.rust-lang.org/cargo/reference/environment-variables.html
    override fun getAdditionalEnvVars(
        state: CargoRunStateBase,
        pkg: CargoWorkspace.Package?
    ): Map<String, String>? {
        val outDir = getOutDir(state, pkg) // TODO: add `?: return null` when the test runner is fixed
        val host = state.cargoProject?.rustcInfo?.version?.host ?: return null
        return mapOf(
            "OUT_DIR" to outDir.toString(),
            "HOST" to host,
            "TARGET" to host, // TODO: replace with an actual target
        )
    }

    private fun getOutDir(state: CargoRunStateBase, pkg: CargoWorkspace.Package?): Path? {
        val configuration = state.runConfiguration
        if (configuration !is CustomBuildConfiguration) return null
        return when {
            configuration.isCustomOutDir -> configuration.customOutDir
            else -> pkg?.outDir?.pathAsPath
        }
    }

    override fun getArtifacts(state: CargoRunStateBase): List<CompilerArtifactMessage> {
        if (state.runConfiguration !is CustomBuildConfiguration) {
            return listOf()
        }
        val crateRoot = state.runConfiguration.crateRoot
        return state.environment.artifacts.orEmpty().filter {
            it.target.cleanKind == CargoMetadata.TargetKind.CUSTOM_BUILD
                && it.target.src_path.toPath() == crateRoot
        }
    }

    // TODO: shouldn't be needed after https://github.com/intellij-rust/intellij-rust/issues/9778 is fixed
    override fun modifyFinalCommand(state: CargoRunStateBase): CargoCommandLine {
        return super.modifyFinalCommand(state).copy(emulateTerminal = true)
    }

    companion object {
        const val RUNNER_ID = "CustomBuildRunner"
    }
}

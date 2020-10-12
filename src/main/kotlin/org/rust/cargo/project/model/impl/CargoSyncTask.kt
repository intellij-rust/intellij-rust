/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.exists
import org.rust.RsTask
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.toolchain.rustup
import org.rust.cargo.toolchain.tools.cargoOrWrapper
import org.rust.cargo.toolchain.tools.rustc
import org.rust.cargo.util.DownloadResult
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.TaskResult
import java.util.concurrent.CompletableFuture

class CargoSyncTask(
    project: Project,
    private val cargoProjects: List<CargoProjectImpl>,
    private val result: CompletableFuture<List<CargoProjectImpl>>
) : Task.Backgroundable(project, "Reloading Cargo projects", true), RsTask {

    override val taskType: RsTask.TaskType
        get() = RsTask.TaskType.CARGO_SYNC

    override val runSyncInUnitTests: Boolean
        get() = true

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val refreshedProjects = try {
            doRun(indicator)
        } catch (e: Throwable) {
            result.completeExceptionally(e)
            throw e
        }
        result.complete(refreshedProjects)
    }

    private fun doRun(indicator: ProgressIndicator): List<CargoProjectImpl> {
        val toolchain = project.toolchain
        val refreshedProjects = if (toolchain == null) {
            project.showBalloon(
                "Cargo project update failed:<br>No Rust toolchain",
                NotificationType.ERROR
            )
            cargoProjects
        } else {
            cargoProjects.map {
                if (!it.workingDirectory.exists()) {
                    it.copy(
                        stdlibStatus = CargoProject.UpdateStatus.UpdateFailed("Project directory does not exist")
                    )
                } else {
                    val context = SyncContext(project, it, toolchain)
                    it.withRustcInfo(fetchRustcInfo(context, indicator))
                        .withWorkspace(fetchCargoWorkspace(context, indicator))
                        .withStdlib(fetchStdlib(context, indicator))
                }
            }
        }

        return refreshedProjects
    }

    data class SyncContext(val project: Project, val oldCargoProject: CargoProjectImpl, val toolchain: RsToolchain)
}

private fun fetchRustcInfo(
    context: CargoSyncTask.SyncContext,
    progress: ProgressIndicator
): TaskResult<RustcInfo> {
    progress.checkCanceled()

    progress.text = "Getting toolchain version"

    if (!context.toolchain.looksLikeValidToolchain()) {
        return TaskResult.Err("Invalid Rust toolchain ${context.toolchain.presentableLocation}")
    }

    val sysroot = context.toolchain.rustc().getSysroot(context.oldCargoProject.workingDirectory)
        ?: return TaskResult.Err("failed to get project sysroot")

    val rustcVersion = context.toolchain.rustc().queryVersion()

    return TaskResult.Ok(RustcInfo(sysroot, rustcVersion))
}

private fun fetchCargoWorkspace(
    context: CargoSyncTask.SyncContext,
    progress: ProgressIndicator
): TaskResult<CargoWorkspace> {
    progress.checkCanceled()

    progress.text = "Updating Cargo"

    val toolchain = context.toolchain
    if (!toolchain.looksLikeValidToolchain()) {
        return TaskResult.Err("Invalid Rust toolchain ${toolchain.presentableLocation}")
    }
    val projectDirectory = context.oldCargoProject.workingDirectory
    val cargo = toolchain.cargoOrWrapper(projectDirectory)
    return try {
        val projectDescriptionData = cargo.fullProjectDescription(context.project, projectDirectory, object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                val text = event.text.trim { it <= ' ' }
                if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                    progress.text = text
                }
            }
        })
        val manifestPath = projectDirectory.resolve("Cargo.toml")

        // Running "cargo rustc -- --print cfg" causes an error when run in a project with multiple targets
        // error: extra arguments to `rustc` can only be passed to one target, consider filtering
        // the package by passing e.g. `--lib` or `--bin NAME` to specify a single target
        // Running "cargo rustc --bin=projectname  -- --print cfg" we can get around this
        // but it also compiles the whole project, which is probably not wanted
        // TODO: This does not query the target specific cfg flags during cross compilation :-(
        val rawCfgOptions = toolchain.rustc().getCfgOptions(projectDirectory) ?: emptyList()
        val cfgOptions = CfgOptions.parse(rawCfgOptions)
        val ws = CargoWorkspace.deserialize(manifestPath, projectDescriptionData, cfgOptions)
        TaskResult.Ok(ws)
    } catch (e: ExecutionException) {
        TaskResult.Err(e.message ?: "Failed to run Cargo")
    }
}

private fun fetchStdlib(
    context: CargoSyncTask.SyncContext,
    progress: ProgressIndicator
): TaskResult<StandardLibrary> {
    progress.checkCanceled()

    progress.text = "Getting Rust stdlib"

    val workingDirectory = context.oldCargoProject.workingDirectory
    if (context.oldCargoProject.doesProjectLooksLikeRustc()) {
        // rust-lang/rust contains stdlib inside the project
        val std = StandardLibrary.fromPath(workingDirectory.toString())
            ?.asPartOfCargoProject()
        if (std != null) {
            return TaskResult.Ok(std)
        }
    }

    val rustup = context.toolchain.rustup(workingDirectory)
    if (rustup == null) {
        val explicitPath = context.project.rustSettings.explicitPathToStdlib
            ?: context.toolchain.rustc().getStdlibFromSysroot(workingDirectory)?.path
        val lib = explicitPath?.let { StandardLibrary.fromPath(it) }
        return when {
            explicitPath == null -> TaskResult.Err("no explicit stdlib or rustup found")
            lib == null -> TaskResult.Err("invalid standard library: $explicitPath")
            else -> TaskResult.Ok(lib)
        }
    }

    return rustup.fetchStdlib()
}


private fun Rustup.fetchStdlib(): TaskResult<StandardLibrary> {
    return when (val download = downloadStdlib()) {
        is DownloadResult.Ok -> {
            val lib = StandardLibrary.fromFile(download.value)
            if (lib == null) {
                TaskResult.Err("Corrupted standard library: ${download.value.presentableUrl}")
            } else {
                TaskResult.Ok(lib)
            }
        }
        is DownloadResult.Err -> TaskResult.Err("Download failed: ${download.error}")
    }
}

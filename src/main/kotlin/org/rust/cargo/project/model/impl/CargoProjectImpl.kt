/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Key
import com.intellij.util.io.systemIndependentPath
import org.jdom.Element
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProject.UpdateStatus
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.util.modules
import org.rust.ide.notifications.showBalloon
import org.rust.utils.AsyncResult
import org.rust.utils.TaskResult
import org.rust.utils.pathAsPath
import org.rust.utils.runAsyncTask
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

private val LOG = Logger.getInstance(CargoProjectsServiceImpl::class.java)

@State(name = "CargoProjects")
class CargoProjectsServiceImpl(
    val project: Project
) : CargoProjectsService, PersistentStateComponent<Element> {
    @Volatile private var cargoProject: CargoProjectImpl? = null

    override val allProjects: Collection<CargoProject>
        get() = listOfNotNull(cargoProject)

    override fun getState(): Element {
        val state = Element("state")
        val cargoProject = cargoProject
        if (cargoProject != null) {
            val cargoProjectElement = Element("cargoProject")
            cargoProjectElement.setAttribute("FILE", cargoProject.manifest.systemIndependentPath)
            state.addContent(cargoProjectElement)
        }

        return state
    }

    override fun loadState(state: Element) {
        val cargoProjectElement = state.getChild("cargoProject")
        setManifest(cargoProjectElement?.getAttributeValue("FILE")?.let { Paths.get(it) })
        firstTimeInint()
    }

    override fun noStateLoaded() {
        val guessManifest = project.modules.asSequence()
            .flatMap { ModuleRootManager.getInstance(it).contentRoots.asSequence() }
            .mapNotNull { it.findChild(RustToolchain.CARGO_TOML) }
            .firstOrNull()
            ?.pathAsPath
        setManifest(guessManifest)
        firstTimeInint()
    }

    private fun setManifest(manifest: Path?) {
        cargoProject = manifest?.let { CargoProjectImpl(it, this) }
    }

    val taskQueue = BackgroundTaskQueue(project, "Cargo update")

    @Volatile private var initDone: Boolean = false
    private fun firstTimeInint() {
        if (initDone) return
        initDone = true
        refreshAllProjects()
    }

    override fun refreshAllProjects() {
        val cargoProject = cargoProject ?: return
        cargoProject.refresh()
            .whenComplete { updatedProject, throwable ->
                if (throwable != null) {
                    LOG.error(throwable)
                    return@whenComplete
                }

                this.cargoProject = updatedProject

                val status = updatedProject.mergedStatus

                if (status is UpdateStatus.UpdateFailed) {
                    project.showBalloon(
                        "Cargo project update failed:<br>${status.reason}",
                        NotificationType.ERROR
                    )
                }
                afterUpdate(listOf(updatedProject))
            }
    }

    private fun afterUpdate(projects: Collection<CargoProject>) {
        ApplicationManager.getApplication().invokeAndWait {
            runWriteAction {
                ProjectRootManagerEx.getInstanceEx(project)
                    .makeRootsChange(EmptyRunnable.getInstance(), false, true)
            }
        }
        project.messageBus.syncPublisher(CargoProjectsService.CARGO_PROJECTS_TOPIC)
            .cargoProjectsUpdated(projects)
    }

    override fun toString(): String {
        return "CargoProjectsService(cargoProject = $cargoProject)"
    }
}

data class CargoProjectImpl(
    override val manifest: Path,
    private val projectService: CargoProjectsServiceImpl,
    private val rawWorkspace: CargoWorkspace? = null,
    private val stdlib: StandardLibrary? = null,
    override val workspaceStatus: CargoProject.UpdateStatus = UpdateStatus.NeedsUpdate,
    override val stdlibStatus: CargoProject.UpdateStatus = UpdateStatus.NeedsUpdate
) : CargoProject {

    private val projectDirectory get() = manifest.parent
    private val project get() = projectService.project
    private val toolchain get() = project.toolchain
    override val workspace: CargoWorkspace? = run {
        val rawWorkspace = rawWorkspace ?: return@run null
        val stdlib = stdlib ?: return@run rawWorkspace
        rawWorkspace.withStdlib(stdlib.crates)
    }

    override val presentableName: String
        get() = manifest.parent.fileName.toString()

    fun refresh(): CompletableFuture<CargoProjectImpl> = refreshStdlib().thenCompose { it.refreshWorkspace() }

    private fun refreshStdlib(): CompletableFuture<CargoProjectImpl> {
        val rustup = toolchain?.rustup(projectDirectory)
        if (rustup == null) {
            val explicitPath = project.rustSettings.explicitPathToStdlib
            val lib = explicitPath?.let { StandardLibrary.fromPath(it) }
            val result = when {
                explicitPath == null -> TaskResult.Err<StandardLibrary>("no explicit stdlib or rustup found")
                lib == null -> TaskResult.Err("invalid standard library: $explicitPath")
                else -> TaskResult.Ok(lib)
            }
            return CompletableFuture.completedFuture(withStdlib(result))
        }
        return fetchStdlib(project, projectService.taskQueue, rustup)
            .thenApply(this::withStdlib)
    }

    private fun withStdlib(result: TaskResult<StandardLibrary>): CargoProjectImpl = when (result) {
        is TaskResult.Ok -> copy(stdlib = result.value, stdlibStatus = UpdateStatus.UpToDate)
        is TaskResult.Err -> copy(stdlibStatus = UpdateStatus.UpdateFailed(result.reason))
    }

    private fun refreshWorkspace(): CompletableFuture<CargoProjectImpl> {
        val toolchain = toolchain ?:
            return CompletableFuture.completedFuture(copy(workspaceStatus = UpdateStatus.UpdateFailed(
                "Can't update Cargo project, no Rust toolchain"
            )))

        return fetchCargoWorkspace(project, projectService.taskQueue, toolchain, projectDirectory)
            .thenApply(this::withWorkspace)
    }

    private fun withWorkspace(result: TaskResult<CargoWorkspace>): CargoProjectImpl = when (result) {
        is TaskResult.Ok -> copy(rawWorkspace = result.value, workspaceStatus = UpdateStatus.UpToDate)
        is TaskResult.Err -> copy(workspaceStatus = UpdateStatus.UpdateFailed(result.reason))
    }

    override fun toString(): String {
        return "CargoProject(manifest = $manifest)"
    }
}

private fun fetchStdlib(
    project: Project,
    queue: BackgroundTaskQueue,
    rustup: Rustup
): AsyncResult<StandardLibrary> {
    return runAsyncTask(project, queue, "Getting Rust stdlib") {
        progress.isIndeterminate = true
        val download = rustup.downloadStdlib()
        when (download) {
            is Rustup.DownloadResult.Ok -> {
                val lib = StandardLibrary.fromFile(download.library)
                if (lib == null) {
                    err("" +
                        "corrupted standard library: ${download.library.presentableUrl}"
                    )
                } else {
                    ok(lib)
                }
            }
            is Rustup.DownloadResult.Err -> err(
                "download failed: ${download.error}"
            )
        }
    }
}

private fun fetchCargoWorkspace(
    project: Project,
    queue: BackgroundTaskQueue,
    toolchain: RustToolchain,
    projectDirectory: Path
): AsyncResult<CargoWorkspace> {
    return runAsyncTask(project, queue, "Updating cargo") {
        progress.isIndeterminate = true
        if (!toolchain.looksLikeValidToolchain()) {
            return@runAsyncTask err(
                "invalid Rust toolchain ${toolchain.presentableLocation}"
            )
        }
        val cargo = toolchain.cargo(projectDirectory)
        try {
            val ws = cargo.fullProjectDescription(project, object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                    val text = event.text.trim { it <= ' ' }
                    if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                        progress.text = text
                    }
                }
            })
            ok(ws)
        } catch (e: ExecutionException) {
            err(e.message ?: "failed to run Cargo")
        }
    }
}

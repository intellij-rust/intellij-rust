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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.Consumer
import com.intellij.util.indexing.LightDirectoryIndex
import com.intellij.util.io.exists
import com.intellij.util.io.systemIndependentPath
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.collectResults
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProject.UpdateStatus
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.project.workspace.impl.CargoTomlWatcher
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.util.modules
import org.rust.ide.notifications.showBalloon
import org.rust.utils.AsyncValue
import org.rust.utils.TaskResult
import org.rust.utils.pathAsPath
import org.rust.utils.runAsyncTask
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val LOG = Logger.getInstance(CargoProjectsServiceImpl::class.java)

@State(name = "CargoProjects")
class CargoProjectsServiceImpl(
    val project: Project
) : CargoProjectsService, PersistentStateComponent<Element> {
    init {
        with(project.messageBus.connect()) {
            subscribe(VirtualFileManager.VFS_CHANGES, CargoTomlWatcher(fun() {
                if (!project.rustSettings.autoUpdateEnabled) return
                refreshAllProjects()
            }))

            subscribe(RustProjectSettingsService.TOOLCHAIN_TOPIC, object : RustProjectSettingsService.ToolchainListener {
                override fun toolchainChanged() {
                    refreshAllProjects()
                }
            })
        }
    }

    val taskQueue = BackgroundTaskQueue(project, "Cargo update")
    private val projects = AsyncValue<List<CargoProjectImpl>>(emptyList())

    private val NO_PROJECT = CargoProjectImpl(Paths.get(""), this)
    private val directoryIndex: LightDirectoryIndex<CargoProjectImpl> =
        LightDirectoryIndex(project, NO_PROJECT, Consumer { index ->
            for (cargoProject in projects.currentState) {
                index.putInfo(cargoProject.rootDir?.parent, cargoProject)
                cargoProject.workspace
                    ?.packages?.forEach { index.putInfo(it.contentRoot, cargoProject) }
            }
        })

    override fun findProjectForFile(file: VirtualFile): CargoProject? =
        directoryIndex.getInfoForFile(file).takeIf { it !== NO_PROJECT }

    override val allProjects: Collection<CargoProject>
        get() = projects.currentState

    override fun attachCargoProject(manifest: Path): Boolean {
        if (isExistingProject(allProjects, manifest)) return false
        modifyProjects { projects ->
            if (isExistingProject(projects, manifest))
                Promise.resolve(projects)
            else
                doRefresh(project, projects + CargoProjectImpl(manifest, this))
        }
        return true
    }

    override fun detachCargoProject(cargoProject: CargoProject) {
        modifyProjects { projects ->
            Promise.resolve(projects.filter { it.manifest != cargoProject.manifest })
        }
    }

    override fun refreshAllProjects(): Promise<List<CargoProject>> =
        modifyProjects { doRefresh(project, it) }
            .then { projects -> projects.map { it as CargoProject } }

    override fun discoverAndRefresh(): Promise<List<CargoProject>> {
        return modifyProjects { projects ->
            if (projects.any { it.manifest.exists() }) return@modifyProjects Promise.resolve(projects)
            val guessManifest = project.modules
                .asSequence()
                .flatMap { ModuleRootManager.getInstance(it).contentRoots.asSequence() }
                .mapNotNull { it.findChild(RustToolchain.CARGO_TOML) }
                .firstOrNull()
                ?: return@modifyProjects Promise.resolve(projects)
            doRefresh(project, listOf(CargoProjectImpl(guessManifest.pathAsPath, this)))
        }.then { projects -> projects.map { it as CargoProject } }
    }

    private fun modifyProjects(
        f: (List<CargoProjectImpl>) -> Promise<List<CargoProjectImpl>>
    ): Promise<List<CargoProjectImpl>> =
        projects.updateAsync(f)
            .then { projects ->
                directoryIndex.resetIndex()
                ApplicationManager.getApplication().invokeAndWait {
                    runWriteAction {
                        ProjectRootManagerEx.getInstanceEx(project)
                            .makeRootsChange(EmptyRunnable.getInstance(), false, true)
                    }
                }
                project.messageBus.syncPublisher(CargoProjectsService.CARGO_PROJECTS_TOPIC)
                    .cargoProjectsUpdated(projects)

                projects
            }

    @TestOnly
    override fun createTestProject(rootDir: VirtualFile, ws: CargoWorkspace) {
        val manifest = rootDir.pathAsPath.resolve("Cargo.toml")
        val testProject = CargoProjectImpl(manifest, this, ws, null, UpdateStatus.UpToDate)
        testProject.setRootDir(rootDir)
        modifyProjects { _ ->
            Promise.resolve(listOf(testProject))
        }
            .blockingGet(1, TimeUnit.MINUTES)
    }

    override fun getState(): Element {
        val state = Element("state")
        for (cargoProject in allProjects) {
            val cargoProjectElement = Element("cargoProject")
            cargoProjectElement.setAttribute("FILE", cargoProject.manifest.systemIndependentPath)
            state.addContent(cargoProjectElement)
        }
        return state
    }

    override fun loadState(state: Element) {
        val loaded = state.getChildren("cargoProject")
            .mapNotNull { it.getAttributeValue("FILE") }
            .map { CargoProjectImpl(Paths.get(it), this) }
        // Refresh projects via `invokeLater` to avoid model modifications
        // while the project is being opened. Use `updateSync` directly
        // instead of `modifyProjects` for this reason
        projects.updateSync { _ -> loaded }
            .done {
                ApplicationManager.getApplication().invokeLater { refreshAllProjects() }
            }
    }

    override fun noStateLoaded() {
        ApplicationManager.getApplication().invokeLater { discoverAndRefresh() }
    }


    override fun toString(): String {
        return "CargoProjectsService(projects = $allProjects)"
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

    private val rootDirCache = AtomicReference<VirtualFile>()
    override val rootDir: VirtualFile?
        get() {
            val cached = rootDirCache.get()
            if (cached != null && cached.isValid) return cached
            val file = LocalFileSystem.getInstance().findFileByIoFile(manifest.parent.toFile())
            rootDirCache.set(file)
            return file
        }

    @TestOnly
    fun setRootDir(dir: VirtualFile) = rootDirCache.set(dir)

    fun refresh(): Promise<CargoProjectImpl> = refreshStdlib().thenAsync { it.refreshWorkspace() }

    private fun refreshStdlib(): Promise<CargoProjectImpl> {
        val rustup = toolchain?.rustup(projectDirectory)
        if (rustup == null) {
            val explicitPath = project.rustSettings.explicitPathToStdlib
            val lib = explicitPath?.let { StandardLibrary.fromPath(it) }
            val result = when {
                explicitPath == null -> TaskResult.Err<StandardLibrary>("no explicit stdlib or rustup found")
                lib == null -> TaskResult.Err("invalid standard library: $explicitPath")
                else -> TaskResult.Ok(lib)
            }
            return Promise.resolve(withStdlib(result))
        }
        return fetchStdlib(project, projectService.taskQueue, rustup).then(this::withStdlib)
    }

    private fun withStdlib(result: TaskResult<StandardLibrary>): CargoProjectImpl = when (result) {
        is TaskResult.Ok -> copy(stdlib = result.value, stdlibStatus = UpdateStatus.UpToDate)
        is TaskResult.Err -> copy(stdlibStatus = UpdateStatus.UpdateFailed(result.reason))
    }

    private fun refreshWorkspace(): Promise<CargoProjectImpl> {
        val toolchain = toolchain ?:
            return Promise.resolve(copy(workspaceStatus = UpdateStatus.UpdateFailed(
                "Can't update Cargo project, no Rust toolchain"
            )))

        return fetchCargoWorkspace(project, projectService.taskQueue, toolchain, projectDirectory)
            .then(this::withWorkspace)
    }

    private fun withWorkspace(result: TaskResult<CargoWorkspace>): CargoProjectImpl = when (result) {
        is TaskResult.Ok -> copy(rawWorkspace = result.value, workspaceStatus = UpdateStatus.UpToDate)
        is TaskResult.Err -> copy(workspaceStatus = UpdateStatus.UpdateFailed(result.reason))
    }

    override fun toString(): String {
        return "CargoProject(manifest = $manifest)"
    }
}

private fun isExistingProject(projects: Collection<CargoProject>, manifest: Path): Boolean {
    if (projects.any { it.manifest == manifest }) return true
    return projects.mapNotNull { it.workspace }.flatMap { it.packages }
        .filter { it.origin == PackageOrigin.WORKSPACE }
        .any { it.rootDirectory == manifest.parent }
}

private fun doRefresh(project: Project, projects: List<CargoProjectImpl>): Promise<List<CargoProjectImpl>> {
    return collectResults(projects.map { it.refresh() })
        .processed { updatedProjects ->
            for (p in updatedProjects) {
                val status = p.mergedStatus
                if (status is UpdateStatus.UpdateFailed) {
                    project.showBalloon(
                        "Cargo project update failed:<br>${status.reason}",
                        NotificationType.ERROR
                    )
                    break
                }
            }
        }
}


private fun fetchStdlib(
    project: Project,
    queue: BackgroundTaskQueue,
    rustup: Rustup
): Promise<TaskResult<StandardLibrary>> {
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
): Promise<TaskResult<CargoWorkspace>> {
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

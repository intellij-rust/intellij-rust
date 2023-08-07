/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.cargo.project.model.impl

import com.intellij.build.BuildContentDescriptor
import com.intellij.build.BuildDescriptor
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import org.rust.RsBundle
import com.intellij.openapi.vfs.VirtualFile
import org.rust.RsTask
import org.rust.bsp.service.BspConnectionService
import org.rust.cargo.CargoConfig
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.ProcessProgressListener
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.runconfig.buildtool.CargoBuildAdapterBase
import org.rust.cargo.runconfig.buildtool.CargoBuildContextBase
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.tools.*
import org.rust.cargo.util.DownloadResult
import org.rust.cargo.util.UnitTestRustcCacheService
import org.rust.openapiext.TaskResult
import org.rust.stdext.RsResult
import org.rust.stdext.mapNotNullToSet
import org.rust.stdext.unwrapOrElse
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import kotlin.io.path.exists

class CargoSyncTask(
    project: Project,
    private val cargoProjects: List<CargoProjectImpl>,
    private val result: CompletableFuture<List<CargoProjectImpl>>
) : Task.Backgroundable(project, RsBundle.message("progress.title.reloading.cargo.projects"), true), RsTask {

    private val serviceName = if (project.service<BspConnectionService>().hasBspServer()) "bsp" else "cargo"
    override val taskType: RsTask.TaskType
        get() = RsTask.TaskType.CARGO_SYNC

    override val runSyncInUnitTests: Boolean
        get() = true

    override fun run(indicator: ProgressIndicator) {
        LOG.info("CargoSyncTask started")
        indicator.isIndeterminate = true
        val start = System.currentTimeMillis()

        val syncProgress = SyncViewManager.createBuildProgress(project)

        val refreshedProjects = try {
            syncProgress.start(createSyncProgressDescriptor(indicator))
            val refreshedProjects = doRun(indicator, syncProgress)
            val isUpdateFailed = refreshedProjects.any { it.mergedStatus is CargoProject.UpdateStatus.UpdateFailed }
            if (isUpdateFailed) {
                syncProgress.fail()
            } else {
                syncProgress.finish()
            }
            refreshedProjects
        } catch (e: Throwable) {
            if (e is ProcessCanceledException) {
                syncProgress.cancel()
            } else {
                syncProgress.fail()
            }
            result.completeExceptionally(e)
            throw e
        }
        result.complete(refreshedProjects)

        val elapsed = System.currentTimeMillis() - start
        LOG.debug("Finished $serviceName sync task in $elapsed ms")
    }

    private fun doRun(
        indicator: ProgressIndicator,
        syncProgress: BuildProgress<BuildProgressDescriptor>
    ): List<CargoProjectImpl> {
        val toolchain = project.toolchain

        @Suppress("UnnecessaryVariable")
        val refreshedProjects = if (toolchain == null) {
            //todo: tg syncProgress.fail(System.currentTimeMillis(), "$serviceName project update failed:\nNo Rust toolchain")
            syncProgress.fail(System.currentTimeMillis(), RsBundle.message("build.event.message.cargo.project.update.failed.no.rust.toolchain"))
            cargoProjects
        } else {
            cargoProjects.map { cargoProject ->
                syncProgress.runWithChildProgress(
                    RsBundle.message("build.event.title.sync.project", cargoProject.presentableName),
                    createContext = { it },
                    action = { childProgress ->
                        if (!cargoProject.workingDirectory.exists()) {
                            childProgress.message(
                                RsBundle.message("tooltip.project.directory.does.not.exist"),
                                RsBundle.message("build.event.message.project.directory.does.not.exist.consider.detaching.project.from.cargo.tool.window", cargoProject.workingDirectory, cargoProject.presentableName),
                                MessageEvent.Kind.ERROR,
                                null
                            )
                            val stdlibStatus = CargoProject.UpdateStatus.UpdateFailed(RsBundle.message("tooltip.project.directory.does.not.exist"))
                            CargoProjectWithStdlib(cargoProject.copy(stdlibStatus = stdlibStatus), null)
                        } else {
                            val context = SyncContext(project, cargoProject, toolchain, indicator, syncProgress.id, childProgress)
                            val rustcInfoResult = fetchRustcInfo(context)
                            val rustcInfo = (rustcInfoResult as? TaskResult.Ok)?.value
                            val cargoProjectWithRustcInfoAndWorkspace = cargoProject.withRustcInfo(rustcInfoResult)
                                .withWorkspace(fetchCargoWorkspace(context, rustcInfo))
                            CargoProjectWithStdlib(
                                cargoProjectWithRustcInfoAndWorkspace,
                                fetchStdlib(context, cargoProjectWithRustcInfoAndWorkspace, rustcInfo)
                            )
                        }
                    }
                )
            }.chooseAndAttachStdlib()
                .deduplicateProjects()
        }

        return refreshedProjects
    }

    private fun createSyncProgressDescriptor(progress: ProgressIndicator): BuildProgressDescriptor {
        val buildContentDescriptor = BuildContentDescriptor(null, null, object : JComponent() {}, RsBundle.message("build.event.title.cargo"))
        buildContentDescriptor.isActivateToolWindowWhenFailed = true
        buildContentDescriptor.isActivateToolWindowWhenAdded = false
        buildContentDescriptor.isNavigateToError = project.rustSettings.autoShowErrorsInEditor
        val refreshAction = ActionManager.getInstance().getAction("Cargo.RefreshCargoProject")
        val descriptor = DefaultBuildDescriptor(Any(), RsBundle.message("build.event.title.$serviceName"), project.basePath!!, System.currentTimeMillis())
            .withContentDescriptor { buildContentDescriptor }
            .withRestartAction(refreshAction)
            .withRestartAction(StopAction(progress))
        return object : BuildProgressDescriptor {
            override fun getTitle(): String = descriptor.title
            override fun getBuildDescriptor(): BuildDescriptor = descriptor
        }
    }

    private fun fetchRustcInfo(context: SyncContext): TaskResult<RustcInfo> {
        return context.runWithChildProgress(RsBundle.message("progress.text.getting.toolchain.version")) { childContext ->
            if (!childContext.toolchain.looksLikeValidToolchain()) {
                val location = childContext.toolchain.presentableLocation
                return@runWithChildProgress TaskResult.Err(RsBundle.message("invalid.rust.toolchain.02", location))
            }

            val bspService: BspConnectionService = context.project.service<BspConnectionService>()
            if (bspService.hasBspServer()) {
                try {
                    val rustcVersion = bspService.getRustcVersion()
                    val sysroot =
                        bspService.getRustcSysroot()
                            ?: return@runWithChildProgress TaskResult.Err(
                                RsBundle.message("failed.to.get.project.sysroot")
                            )
                    return@runWithChildProgress TaskResult.Ok(
                        RustcInfo(sysroot, rustcVersion, null, null)
                    )
                } catch (e: NoSuchElementException) {
                    return@runWithChildProgress TaskResult.Err(
                          RsBundle.message(RsBundle.message("build.event.title.failed.to.fetch.rustc.version"))
                    )
                }
            }

            val workingDirectory = childContext.oldCargoProject.workingDirectory

            val listener = RustcVersionProcessAdapter(childContext)
            val rustcVersion = childContext.toolchain.rustc()
                .queryVersion(workingDirectory, context.project, listener)
                .unwrapOrElse {
                    LOG.warn("Failed to fetch rustc version", it)
                    context.error(RsBundle.message("build.event.title.failed.to.fetch.rustc.version"), it.message.orEmpty())
                    null
                }
            val sysroot = UnitTestRustcCacheService.cached(rustcVersion) {
                childContext.toolchain.rustc().getSysroot(workingDirectory)
            } ?: return@runWithChildProgress TaskResult.Err(RsBundle.message("failed.to.get.project.sysroot"))
            val rustupActiveToolchain = UnitTestRustcCacheService.cached(rustcVersion) {
                childContext.toolchain.rustup(workingDirectory)?.activeToolchainName()
            }
            val rustcTargets = UnitTestRustcCacheService.cached(rustcVersion) {
                childContext.toolchain.rustc().getTargets(workingDirectory)
            }

            TaskResult.Ok(RustcInfo(sysroot, rustcVersion, rustupActiveToolchain, rustcTargets))
        }
    }

    companion object {
        private val LOG = logger<CargoSyncTask>()
    }

    private class StopAction(private val progress: ProgressIndicator) :
        DumbAwareAction({ "Stop" }, AllIcons.Actions.Suspend) {

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = progress.isRunning
        }

        override fun actionPerformed(e: AnActionEvent) {
            progress.cancel()
        }
    }

    data class SyncContext(
        val project: Project,
        val oldCargoProject: CargoProjectImpl,
        val toolchain: RsToolchainBase,
        val progress: ProgressIndicator,
        val buildId: Any,
        val syncProgress: BuildProgress<BuildProgressDescriptor>
    ) {

        val id: Any get() = syncProgress.id

        fun <T> runWithChildProgress(
            @NlsContexts.ProgressText title: String,
            action: (SyncContext) -> TaskResult<T>
        ): TaskResult<T> {
            progress.checkCanceled()
            progress.text = title

            return syncProgress.runWithChildProgress(title, { copy(syncProgress = it) }, action) { childProgress, result ->
                when (result) {
                    is TaskResult.Ok -> childProgress.finish()
                    is TaskResult.Err -> {
                        childProgress.message(result.reason, result.message.orEmpty(), MessageEvent.Kind.ERROR, null)
                        childProgress.fail()
                    }
                }
            }
        }

        fun withProgressText(@NlsContexts.ProgressText @NlsContexts.ProgressTitle text: String) {
            progress.text = text
            syncProgress.progress(text)
        }
    }
}

private class CargoProjectWithStdlib(
    val cargoProject: CargoProjectImpl,
    val stdlib: TaskResult<StandardLibrary>?
)

private class CargoProjectWithExistingStdlib(
    @Suppress("unused") val cargoProject: CargoProjectImpl,
    val rustcVersion: RustcVersion,
    val stdlib: StandardLibrary
)

/**
 * Intellij-Rust plugin currently supports only one stdlib at a time. If there are different standard libraries in
 * different cargo projects, select and use the most recent of them.
 * A cargo project may have a different stdlib if there is a `rust-toolchain.toml` file,
 * or if it uses `rustup override`
 */
private fun List<CargoProjectWithStdlib>.chooseAndAttachStdlib(): List<CargoProjectImpl> {
    val projectsWithStdlib = mapNotNull {
        val rustcVersion = it.cargoProject.rustcInfo?.version ?: return@mapNotNull null
        val stdlib = (it.stdlib as? TaskResult.Ok)?.value ?: return@mapNotNull null
        CargoProjectWithExistingStdlib(it.cargoProject, rustcVersion, stdlib)
    }
    val embeddedStdlib = projectsWithStdlib.find { it.stdlib.isPartOfCargoProject }
    val theMostRecentStdlib = embeddedStdlib
        ?: projectsWithStdlib.maxByOrNull { it.rustcVersion.semver }
    return map {
        when {
            it.stdlib == null -> it.cargoProject
            it.stdlib is TaskResult.Err -> it.cargoProject.withStdlib(it.stdlib)
            theMostRecentStdlib != null -> it.cargoProject.withStdlib(TaskResult.Ok(theMostRecentStdlib.stdlib))
            else -> it.cargoProject.withStdlib(it.stdlib)
        }
    }
}

/**
 * There are 2 kinds of possible cargo project duplication:
 *
 * 1. Direct duplication: when there are 2 projects with the same [CargoProject.manifest] value.
 *    Looks like it's impossible to make such duplication without editing workspace.xml.
 * 2. Duplication of workspace members: when there is a project with cargo workspace consist of
 *    several packages and one of them is also added as a separate CargoProject. It's possible to
 *    make such duplication: initially, there should be 2 independent CargoProjects, but then,
 *    one of them should become a workspace member of the other project (a user can do this by
 *    modifying Cargo.toml)
 *
 * See tests in `CargoProjectDeduplicationTest`
 *
 * Keep in sync with [org.rust.cargo.project.model.impl.isExistingProject]
 */
private fun List<CargoProjectImpl>.deduplicateProjects(): List<CargoProjectImpl> {
    val projects = distinctBy { it.manifest }

    // Maps `project root` -> [package root] for workspace packages
    val projectRootToWorkspacePackages: Map<Path, Set<Path>> = projects.associate { project ->
        project.manifest.parent to run {
            val workspace = project.rawWorkspace ?: return@run emptySet()
            workspace.packages.mapNotNullToSet { pkg ->
                if (pkg.origin != PackageOrigin.WORKSPACE) return@mapNotNullToSet null
                pkg.rootDirectory
            }
        }
    }

    val projectsToRemove = mutableSetOf<CargoProjectImpl>()

    // Consider this setup:
    // ```
    // +-- root-project // <- attached as cargo project
    // |   +-- Cargo.toml // workspace.members = ["subpackage"]
    // |   +-- subpackage // <- attached as cargo project (should be removed)
    // |       +-- Cargo.toml
    // ```
    // In this case, we want to remove `subpackage` project and leave only `root-project`.
    // Note that we want to remove it ONLY if `subpackage` is a workspace member of `root-project`.
    // Cargo requires that a workspace root is always an ancestor of its members in file hierarchy,
    // so here we iterate ancestor directories of each cargo project root and removing the project if
    // there is a project that includes this one as a workspace package
    for (project in projects) {
        val projectRootPath = project.manifest.parent
        for (ancestorPath in generateSequence(projectRootPath.parent) { it.parent }) {
            val ancestorProjectPackageRoots = projectRootToWorkspacePackages[ancestorPath]
            if (ancestorProjectPackageRoots != null && projectRootPath in ancestorProjectPackageRoots) {
                projectsToRemove += project
            }
        }
    }

    return projects.filter { it !in projectsToRemove }
}

private fun fetchCargoWorkspace(context: CargoSyncTask.SyncContext, rustcInfo: RustcInfo?): TaskResult<CargoWorkspace> {
    return context.runWithChildProgress(RsBundle.message("progress.text.updating.workspace.info")) { childContext ->

        val serviceName = if (context.project.service<BspConnectionService>().hasBspServer()) "cargo" else "bsp"
        val toolchain = childContext.toolchain
        if (!toolchain.looksLikeValidToolchain()) {
            return@runWithChildProgress TaskResult.Err(RsBundle.message("invalid.rust.toolchain.0", toolchain.presentableLocation))
        }
        val projectDirectory = childContext.oldCargoProject.workingDirectory
        val cargo = toolchain.cargoOrWrapper(projectDirectory)

        val cargoConfigResult = UnitTestRustcCacheService.cached(
            rustcInfo?.version,
            cacheIf = { !projectDirectory.resolve(".cargo").exists() }
        ) { cargo.getConfig(childContext.project, projectDirectory) }
        val cargoConfig = when (cargoConfigResult) {
            is RsResult.Ok -> cargoConfigResult.ok
            is RsResult.Err -> {
                val message = RsBundle.message("build.event.message.fetching.$serviceName.config.failed", cargoConfigResult.err.message.orEmpty())
                childContext.warning(RsBundle.message("build.event.title.fetching.$serviceName.config"), message)
                CargoConfig.DEFAULT
            }
        }

        CargoEventService.getInstance(childContext.project).onMetadataCall(projectDirectory)
        val buildTargets = cargoConfig.buildTargets.ifEmpty { listOfNotNull(rustcInfo?.version?.host) }
        val (projectDescriptionData, status) = cargo.fullProjectDescription(
            childContext.project,
            projectDirectory,
            buildTargets,
            rustcInfo?.version,
        ) {
            when (it) {
                CargoCallType.METADATA -> SyncProcessAdapter(childContext)
                CargoCallType.BUILD_SCRIPT_CHECK -> {
                    val childProgress = childContext.syncProgress.startChildProgress(RsBundle.message("build.event.title.build.scripts.evaluation"))
                    val syncContext = childContext.copy(syncProgress = childProgress)

                    val buildContext = SyncCargoBuildContext(
                        childContext.oldCargoProject,
                        buildId = syncContext.buildId,
                        parentId = syncContext.id,
                        progressIndicator = syncContext.progress
                    )

                    SyncCargoBuildAdapter(syncContext, buildContext)
                }
            }
        }.unwrapOrElse { return@runWithChildProgress TaskResult.Err(RsBundle.message("failed.to.run.cargo"), it.message) }
        if (status == ProjectDescriptionStatus.BUILD_SCRIPT_EVALUATION_ERROR) {
            childContext.warning(
                RsBundle.message("build.event.title.build.scripts.evaluation.failed"),
                RsBundle.message("build.event.message.build.scripts.evaluation.failed.features.based.on.generated.info.by.build.scripts.may.not.work.in.your.ide")
            )
        }

        val manifestPath = projectDirectory.resolve("Cargo.toml")

        val cfgOptionsResult = UnitTestRustcCacheService.cached(
            rustcInfo?.version,
            cacheIf = { !projectDirectory.resolve(".cargo").exists() }
        ) { cargo.getCfgOption(childContext.project, projectDirectory) }

        val useBSP: Boolean = childContext.project.service<BspConnectionService>().hasBspServer()
        val cfgOptions = when (cfgOptionsResult) {
            is RsResult.Ok -> cfgOptionsResult.ok
            is RsResult.Err -> {
                if (!useBSP) {
                    val message = RsBundle.message("build.event.message.fetching.target.specific.cfg.options.failed.fallback.to.host.options", cfgOptionsResult.err.message.orEmpty())
                    childContext.warning(RsBundle.message("build.event.title.fetching.target.specific.cfg.options"), message)
                }
                toolchain.rustc().getCfgOptions(projectDirectory)
            }
        }

        val ws = CargoWorkspace.deserialize(manifestPath, projectDescriptionData, cfgOptions, cargoConfig)
        TaskResult.Ok(ws)
    }
}

private fun fetchStdlib(context: CargoSyncTask.SyncContext, cargoProject: CargoProjectImpl, rustcInfo: RustcInfo?): TaskResult<StandardLibrary> {
    return context.runWithChildProgress(RsBundle.message("progress.text.getting.rust.stdlib")) { childContext ->

        val workingDirectory = cargoProject.workingDirectory
        if (cargoProject.doesProjectLooksLikeRustc()) {
            // rust-lang/rust contains stdlib inside the project
            val std = StandardLibrary.fromPath(
                childContext.project,
                workingDirectory.toString(),
                rustcInfo,
                isPartOfCargoProject = true
            )
            if (std != null) {
                return@runWithChildProgress TaskResult.Ok(std)
            }
        }

        val cargoConfig = cargoProject.rawWorkspace?.cargoConfig ?: CargoConfig.DEFAULT
        val rustup = childContext.toolchain.rustup(workingDirectory)
        if (rustup == null) {
            val explicitPath = childContext.project.rustSettings.explicitPathToStdlib
                ?: childContext.toolchain.rustc().getStdlibFromSysroot(workingDirectory)?.path
            val lib = explicitPath?.let { StandardLibrary.fromPath(childContext.project, it, rustcInfo, cargoConfig) }
            return@runWithChildProgress when {
                explicitPath == null -> TaskResult.Err(RsBundle.message("no.explicit.stdlib.or.rustup.found"))
                lib == null -> TaskResult.Err(RsBundle.message("invalid.standard.library.0", explicitPath))
                else -> TaskResult.Ok(lib)
            }
        }

        rustup.fetchStdlib(childContext, rustcInfo, cargoConfig)
    }
}


private fun Rustup.fetchStdlib(
    context: CargoSyncTask.SyncContext,
    rustcInfo: RustcInfo?,
    cargoConfig: CargoConfig
): TaskResult<StandardLibrary> {
    val bspService = context.project.service<BspConnectionService>()
    if (bspService.hasBspServer()) {
        val stdlib: VirtualFile? = try {
            bspService.getStdLibPath()
        } catch (e: NoSuchElementException) {
            return TaskResult.Err(RsBundle.message("failed.to.get.standard.library.0", e.message ?: ""))
        }
        return if (stdlib != null) {
            val lib = StandardLibrary.fromFile(context.project, stdlib, rustcInfo, cargoConfig, listener = SyncProcessAdapter(context), useBsp = true)
            if (lib == null) {
                TaskResult.Err(RsBundle.message("corrupted.standard.library.0", stdlib.presentableUrl))
            } else {
                TaskResult.Ok(lib)
            }
        } else {
            TaskResult.Err(RsBundle.message("failed.to.fetch.standard.library.from.bsp"))
        }
    }
    return when (val download = UnitTestRustcCacheService.cached(rustcInfo?.version) { downloadStdlib() }) {
        is DownloadResult.Ok -> {
            val lib = StandardLibrary.fromFile(context.project, download.value, rustcInfo, cargoConfig, listener = SyncProcessAdapter(context))
            if (lib == null) {
                TaskResult.Err(RsBundle.message("corrupted.standard.library.0", download.value.presentableUrl))
            } else {
                TaskResult.Ok(lib)
            }
        }
        is DownloadResult.Err -> TaskResult.Err(RsBundle.message("download.failed.0", download.error))
    }
}

private fun <T, R> BuildProgress<BuildProgressDescriptor>.runWithChildProgress(
    @BuildEventsNls.Title title: String,
    createContext: (BuildProgress<BuildProgressDescriptor>) -> T,
    action: (T) -> R,
    onResult: (BuildProgress<BuildProgressDescriptor>, R) -> Unit = { progress, _ -> progress.finish() }
): R {
    val childProgress = startChildProgress(title)
    try {
        val context = createContext(childProgress)
        val result = action(context)
        onResult(childProgress, result)
        return result
    } catch (e: Throwable) {
        if (e is ProcessCanceledException) {
            cancel()
        } else {
            fail()
        }
        throw e
    }
}

private class SyncProcessAdapter(
    private val context: CargoSyncTask.SyncContext
) : ProcessAdapter(),
    ProcessProgressListener {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
        val text = event.text.trim { it <= ' ' }
        if (text.startsWith("Updating") || text.startsWith("Downloading")) {
            context.withProgressText(text)
        }
        if (text.startsWith("Vendoring")) {
            // This code expect that vendoring message has the following format:
            // "Vendoring %package_name% v%package_version% (%src_dir%) to %dst_dir%".
            // So let's extract "Vendoring %package_name% v%package_version%" part and show it for users
            val index = text.indexOf(" (")
            val progressText = if (index != -1) text.substring(0, index) else text
            context.withProgressText(progressText)
        }
    }

    override fun error(title: String, message: String) = context.error(title, message)
    override fun warning(title: String, message: String) = context.warning(title, message)
}

private class RustcVersionProcessAdapter(
    private val context: CargoSyncTask.SyncContext
) : ProcessAdapter() {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
        val text = event.text.trim { it <= ' ' }
        if (text.startsWith("info:")) {
            context.withProgressText(text.removePrefix(RsBundle.message("progress.text.info")).trim())
        }
    }
}

private class SyncCargoBuildContext(
    cargoProject: CargoProject,
    buildId: Any,
    parentId: Any,
    progressIndicator: ProgressIndicator
) : CargoBuildContextBase(cargoProject, RsBundle.message("progress.text.building"), false, buildId, parentId) {
    init {
        indicator = progressIndicator
    }
}

private class SyncCargoBuildAdapter(
    private val context: CargoSyncTask.SyncContext,
    buildContext: CargoBuildContextBase
) : CargoBuildAdapterBase(buildContext, context.project.service<SyncViewManager>()) {

    override fun onBuildOutputReaderFinish(
        event: ProcessEvent,
        isSuccess: Boolean,
        isCanceled: Boolean,
        error: Throwable?
    ) {
        when {
            isSuccess -> context.syncProgress.finish()
            isCanceled -> context.syncProgress.cancel()
            else -> context.syncProgress.fail()
        }
    }
}

private fun CargoSyncTask.SyncContext.error(
    @BuildEventsNls.Title title: String,
    @BuildEventsNls.Message message: String
) {
    syncProgress.message(title, message, MessageEvent.Kind.ERROR, null)
}

private fun CargoSyncTask.SyncContext.warning(
    @BuildEventsNls.Title title: String,
    @BuildEventsNls.Message message: String
) {
    syncProgress.message(title, message, MessageEvent.Kind.WARNING, null)
}

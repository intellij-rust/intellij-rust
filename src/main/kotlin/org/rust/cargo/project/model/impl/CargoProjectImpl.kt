/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.RunManager
import com.intellij.ide.impl.isTrusted
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.ui.GuiUtils
import com.intellij.util.indexing.LightDirectoryIndex
import com.intellij.util.io.exists
import com.intellij.util.io.systemIndependentPath
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProject.UpdateStatus
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.setup
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.RustProjectSettingsService.RustSettingsChangedEvent
import org.rust.cargo.project.settings.RustProjectSettingsService.RustSettingsListener
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.toolwindow.CargoToolWindow.Companion.initializeToolWindow
import org.rust.cargo.project.workspace.*
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.notifications.showBalloon
import org.rust.ide.security.isNewTrustedProjectApiAvailable
import org.rust.ide.security.whenProjectTrusted
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.openapiext.TaskResult
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.modules
import org.rust.openapiext.pathAsPath
import org.rust.stdext.AsyncValue
import org.rust.stdext.applyWithSymlink
import org.rust.stdext.exhaustive
import org.rust.stdext.mapNotNullToSet
import org.rust.taskQueue
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference


@State(name = "CargoProjects", storages = [
    Storage(StoragePathMacros.WORKSPACE_FILE),
    Storage("misc.xml", deprecated = true)
])
open class CargoProjectsServiceImpl(
    final override val project: Project
) : CargoProjectsService, PersistentStateComponent<Element>, Disposable {
    init {
        with(project.messageBus.connect()) {
            if (!isUnitTestMode) {
                subscribe(VirtualFileManager.VFS_CHANGES, CargoTomlWatcher(this@CargoProjectsServiceImpl, fun() {
                    if (!project.rustSettings.autoUpdateEnabled) return
                    refreshAllProjects()
                }))
            }

            subscribe(RustProjectSettingsService.RUST_SETTINGS_TOPIC, object : RustSettingsListener {
                override fun rustSettingsChanged(e: RustSettingsChangedEvent) {
                    if (e.affectsCargoMetadata) {
                        refreshAllProjects()
                    }
                }
            })

            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, CargoProjectsListener { _, _ ->
                StartupManager.getInstance(project).runAfterOpened {
                    GuiUtils.invokeLaterIfNeeded({
                        initializeToolWindow(project)
                    }, ModalityState.NON_MODAL)
                }
            })
        }
        // BACKCOMPAT: 2021.3. Just declare `com.intellij.ide.impl.TrustStateListener`
        //  instead of `org.rust.ide.security.RsTrustChangeNotifier`
        if (isNewTrustedProjectApiAvailable) {
            @Suppress("LeakingThis")
            whenProjectTrusted(this) {
                refreshAllProjects()
            }
        }
    }

    /**
     * The heart of the plugin Project model. Care must be taken to ensure
     * this is thread-safe, and that refreshes are scheduled after
     * set of projects changes.
     */
    private val projects = AsyncValue<List<CargoProjectImpl>>(emptyList())


    @Suppress("LeakingThis")
    private val noProjectMarker = CargoProjectImpl(Paths.get(""), this)

    /**
     * [directoryIndex] allows to quickly map from a [VirtualFile] to
     * a containing [CargoProject].
     */
    private val directoryIndex: LightDirectoryIndex<CargoProjectImpl> =
        LightDirectoryIndex(project, noProjectMarker) { index ->
            val visited = mutableSetOf<VirtualFile>()

            fun VirtualFile.put(cargoProject: CargoProjectImpl) {
                if (this in visited) return
                visited += this
                index.putInfo(this, cargoProject)
            }

            fun CargoWorkspace.Package.put(cargoProject: CargoProjectImpl) {
                contentRoot?.put(cargoProject)
                outDir?.put(cargoProject)
                for (additionalRoot in additionalRoots()) {
                    additionalRoot.put(cargoProject)
                }
                for (target in targets) {
                    target.crateRoot?.parent?.put(cargoProject)
                }
            }

            val lowPriority = mutableListOf<Pair<CargoWorkspace.Package, CargoProjectImpl>>()

            for (cargoProject in projects.currentState) {
                cargoProject.rootDir?.put(cargoProject)
                for (pkg in cargoProject.workspace?.packages.orEmpty()) {
                    if (pkg.origin == PackageOrigin.WORKSPACE) {
                        pkg.put(cargoProject)
                    } else {
                        lowPriority += pkg to cargoProject
                    }
                }
            }

            for ((pkg, cargoProject) in lowPriority) {
                pkg.put(cargoProject)
            }
        }

    @Suppress("LeakingThis")
    private val packageIndex: CargoPackageIndex = CargoPackageIndex(project, this)

    override val allProjects: Collection<CargoProject>
        get() = projects.currentState

    override val hasAtLeastOneValidProject: Boolean
        get() = hasAtLeastOneValidProject(allProjects)

    // Guarded by the platform RWLock
    override var initialized: Boolean = false

    private var isLegacyRustNotificationShowed: Boolean = false

    override fun findProjectForFile(file: VirtualFile): CargoProject? =
        file.applyWithSymlink { directoryIndex.getInfoForFile(it).takeIf { info -> info !== noProjectMarker } }

    override fun findPackageForFile(file: VirtualFile): CargoWorkspace.Package? =
        file.applyWithSymlink(packageIndex::findPackageForFile)

    override fun attachCargoProject(manifest: Path): Boolean {
        if (isExistingProject(allProjects, manifest)) return false
        modifyProjects { projects ->
            if (isExistingProject(projects, manifest))
                CompletableFuture.completedFuture(projects)
            else
                doRefresh(project, projects + CargoProjectImpl(manifest, this))
        }
        return true
    }

    override fun attachCargoProjects(vararg manifests: Path) {
        val manifests2 = manifests.filter { !isExistingProject(allProjects, it) }
        if (manifests2.isEmpty()) return
        modifyProjects { projects ->
            val newManifests3 = manifests2.filter { !isExistingProject(projects, it) }
            if (newManifests3.isEmpty())
                CompletableFuture.completedFuture(projects)
            else
                doRefresh(project, projects + newManifests3.map { CargoProjectImpl(it, this) })
        }
    }

    override fun detachCargoProject(cargoProject: CargoProject) {
        modifyProjects { projects ->
            CompletableFuture.completedFuture(projects.filter { it.manifest != cargoProject.manifest })
        }
    }

    override fun refreshAllProjects(): CompletableFuture<out List<CargoProject>> =
        modifyProjects { doRefresh(project, it) }

    override fun discoverAndRefresh(): CompletableFuture<out List<CargoProject>> {
        val guessManifest = suggestManifests().firstOrNull()
            ?: return CompletableFuture.completedFuture(projects.currentState)

        return modifyProjects { projects ->
            if (hasAtLeastOneValidProject(projects)) return@modifyProjects CompletableFuture.completedFuture(projects)
            doRefresh(project, listOf(CargoProjectImpl(guessManifest.pathAsPath, this)))
        }
    }

    override fun suggestManifests(): Sequence<VirtualFile> =
        project.modules
            .asSequence()
            .flatMap { ModuleRootManager.getInstance(it).contentRoots.asSequence() }
            .mapNotNull { it.findChild(CargoConstants.MANIFEST_FILE) }

    /**
     * Modifies [CargoProject.userDisabledFeatures] that eventually affects [CargoWorkspace.Package.featureState].
     * Note that [CargoProject] is immutable object, so [cargoProject] instance is not mutated by [modifyFeatures]
     * invocation. Instead, new [CargoProject] instance is created and replaces this instance in
     * [CargoProjectsService.allProjects].
     *
     * Here we only modify [CargoProject.userDisabledFeatures]. The final feature state
     * is inferred in [WorkspaceImpl.inferFeatureState].
     *
     * See tests in `CargoFeaturesModificationTest`
     */
    override fun modifyFeatures(cargoProject: CargoProject, features: Set<PackageFeature>, newState: FeatureState) {
        modifyProjectFeatures(cargoProject) { workspace, userDisabledFeatures ->
            val packagesByRoots = workspace.packages.associateBy { it.rootDirectory }
            val actualFeatures = features.mapNotNullToSet { f ->
                packagesByRoots[f.pkg.rootDirectory]?.let { PackageFeature(it, f.name) }
            }
            when (newState) {
                FeatureState.Disabled -> {
                    // When a user disables a feature, all we have to do is add it into `userDisabledFeatures`.
                    // The state of dependant features will be inferred in `WorkspaceImpl.inferFeatureState`
                    for (feature in actualFeatures) {
                        userDisabledFeatures.setFeatureState(feature, FeatureState.Disabled)
                    }
                }
                FeatureState.Enabled -> {
                    // But when disables, we should ensure `userDisabledFeatures` state is consistent.
                    //
                    // For example, consider such a case:
                    // (let [x] mean the feature was enabled automatically (by default),
                    // [d] – disabled because of presence in `userDisabledFeatures`,
                    // [ ] – disabled automatically because of dependency on [d] feature)
                    //
                    // [x] f1 = ["f3"]
                    // [x] f2 = ["f3"]
                    // [x] f3 = []
                    //
                    // Then a user disables `f3`:
                    // [ ] f1 = ["f3"]
                    // [ ] f2 = ["f3"]
                    // [d] f3 = []
                    // `f1` and `f2` disabled automatically because they depend on `f3`.
                    //
                    // Then a user enables `f1`, and this is our case!
                    // [x] f1 = ["f3"]
                    // [d] f2 = ["f3"]
                    // [x] f3 = []
                    // To enable `f1`, `f3` should be removed from `userDisabledFeatures` (because `f1`
                    // depends on `f3`). But if we just remove `f3` from `userDisabledFeatures`, `f2` will
                    // also become enabled, but we don't want it! So we have to remove `f3` and add `f2`

                    workspace.featureGraph.apply(defaultState = FeatureState.Enabled) {
                        disableAll(userDisabledFeatures.getDisabledFeatures(workspace.packages))
                        enableAll(actualFeatures)
                    }.forEach { (feature, state) ->
                        if (feature.pkg.origin == PackageOrigin.WORKSPACE) {
                            userDisabledFeatures.setFeatureState(feature, state)
                        }
                    }
                }
            }.exhaustive
        }
    }

    private fun modifyProjectFeatures(
        cargoProject: CargoProject,
        action: (CargoWorkspace, MutableUserDisabledFeatures) -> Unit
    ) {
        modifyProjectsLite { projects ->
            val oldProject = projects.singleOrNull { it.manifest == cargoProject.manifest }
                ?: return@modifyProjectsLite projects

            val workspace = oldProject.workspace ?: return@modifyProjectsLite projects

            val userDisabledFeatures = oldProject.userDisabledFeatures.toMutable()

            action(workspace, userDisabledFeatures)

            val newProject = oldProject.copy(userDisabledFeatures = userDisabledFeatures)
            val newProjects = projects.toMutableList()

            // This can't fail because we got `oldProject` from `projects` few lines above
            newProjects[newProjects.indexOf(oldProject)] = newProject
            newProjects
        }
    }

    /**
     * All modifications to project model except for low-level `loadState` should
     * go through this method: it makes sure that when we update various IDEA listeners,
     * [allProjects] contains fresh projects.
     */
    protected fun modifyProjects(
        f: (List<CargoProjectImpl>) -> CompletableFuture<List<CargoProjectImpl>>
    ): CompletableFuture<List<CargoProjectImpl>> =
        projects.updateAsync(f)
            .thenApply { projects ->
                invokeAndWaitIfNeeded {
                    val fileTypeManager = FileTypeManager.getInstance()
                    runWriteAction {
                        if (projects.isNotEmpty()) {
                            checkRustVersion(projects)

                            // Android RenderScript (from Android plugin) files has the same extension (.rs) as Rust files.
                            // In some cases, IDEA determines `*.rs` files have RenderScript file type instead of Rust one
                            // that leads any code insight features don't work in Rust projects.
                            // See https://youtrack.jetbrains.com/issue/IDEA-237376
                            //
                            // It's a hack to provide proper mapping when we are sure that it's Rust project
                            fileTypeManager.associateExtension(RsFileType, RsFileType.defaultExtension)
                        }

                        directoryIndex.resetIndex()
                        // In unit tests roots change is done by the test framework in most cases
                        runWithNonLightProject(project) {
                            ProjectRootManagerEx.getInstanceEx(project)
                                .makeRootsChange(EmptyRunnable.getInstance(), false, true)
                        }
                        project.messageBus.syncPublisher(CargoProjectsService.CARGO_PROJECTS_TOPIC)
                            .cargoProjectsUpdated(this, projects)
                        initialized = true
                    }
                }

                projects
            }

    private fun modifyProjectsLite(
        f: (List<CargoProjectImpl>) -> List<CargoProjectImpl>
    ): CompletableFuture<List<CargoProjectImpl>> =
        projects.updateSync(f)
            .thenApply { projects ->
                invokeAndWaitIfNeeded {
                    val psiManager = PsiManager.getInstance(project)
                    runWriteAction {
                        directoryIndex.resetIndex()
                        project.messageBus.syncPublisher(CargoProjectsService.CARGO_PROJECTS_TOPIC)
                            .cargoProjectsUpdated(this, projects)
                        psiManager.dropPsiCaches()
                        DaemonCodeAnalyzer.getInstance(project).restart()
                    }
                }

                projects
            }

    private fun checkRustVersion(projects: List<CargoProjectImpl>) {
        val minToolchainVersion = projects.asSequence()
            .mapNotNull { it.rustcInfo?.version?.semver }
            .minOrNull()
        val isUnsupportedRust = minToolchainVersion != null &&
            minToolchainVersion < RsToolchainBase.MIN_SUPPORTED_TOOLCHAIN
        @Suppress("LiftReturnOrAssignment")
        if (isUnsupportedRust) {
            if (!isLegacyRustNotificationShowed) {
                val content = "Rust <b>$minToolchainVersion</b> is no longer supported. " +
                    "It may lead to unexpected errors. " +
                    "Consider upgrading your toolchain to at least <b>${RsToolchainBase.MIN_SUPPORTED_TOOLCHAIN}</b>"
                project.showBalloon(content, NotificationType.WARNING)
            }
            isLegacyRustNotificationShowed = true
        } else {
            isLegacyRustNotificationShowed = false
        }
    }

    override fun getState(): Element {
        val state = Element("state")
        for (cargoProject in allProjects) {
            val cargoProjectElement = Element("cargoProject")
            cargoProjectElement.setAttribute("FILE", cargoProject.manifest.systemIndependentPath)
            state.addContent(cargoProjectElement)
        }

        // Note that if [state] is empty (there are no cargo projects), [noStateLoaded] will be called on the next load

        return state
    }

    override fun loadState(state: Element) {
        // [cargoProjects] is non-empty here. Otherwise, [noStateLoaded] is called instead of [loadState]
        val cargoProjects = state.getChildren("cargoProject")
        val loaded = mutableListOf<CargoProjectImpl>()

        val userDisabledFeaturesMap = project.service<UserDisabledFeaturesHolder>()
            .takeLoadedUserDisabledFeatures()

        for (cargoProject in cargoProjects) {
            val file = cargoProject.getAttributeValue("FILE")
            val manifest = Paths.get(file)
            val userDisabledFeatures = userDisabledFeaturesMap[manifest] ?: UserDisabledFeatures.EMPTY
            val newProject = CargoProjectImpl(manifest, this, userDisabledFeatures)
            loaded.add(newProject)
        }

        // Wake the macro expansion service as soon as possible.
        project.macroExpansionManager

        // Refresh projects via `invokeLater` to avoid model modifications
        // while the project is being opened. Use `updateSync` directly
        // instead of `modifyProjects` for this reason
        projects.updateSync { loaded }
            .whenComplete { _, _ ->
                invokeLater {
                    if (project.isDisposed) return@invokeLater
                    refreshAllProjects()
                }
            }
    }

    /**
     * Note that [noStateLoaded] is called not only during the first service creation, but on any
     * service load if [getState] returned empty state during previous save (i.e. there are no cargo project)
     */
    override fun noStateLoaded() {
        // Do nothing: in theory, we might try to do [discoverAndRefresh]
        // here, but the `RsToolchain` is most likely not ready.
        //
        // So the actual "Let's guess a project model if it is not imported
        // explicitly" happens in [org.rust.ide.notifications.MissingToolchainNotificationProvider]

        initialized = true // No lock required b/c it's service init time

        // Should be initialized with this service because it stores a part of cargo projects data
        project.service<UserDisabledFeaturesHolder>()
    }

    override fun dispose() {}

    override fun toString(): String =
        "CargoProjectsService(projects = $allProjects)"
}

data class CargoProjectImpl(
    override val manifest: Path,
    private val projectService: CargoProjectsServiceImpl,
    override val userDisabledFeatures: UserDisabledFeatures = UserDisabledFeatures.EMPTY,
    val rawWorkspace: CargoWorkspace? = null,
    private val stdlib: StandardLibrary? = null,
    override val rustcInfo: RustcInfo? = null,
    override val workspaceStatus: UpdateStatus = UpdateStatus.NeedsUpdate,
    override val stdlibStatus: UpdateStatus = UpdateStatus.NeedsUpdate,
    override val rustcInfoStatus: UpdateStatus = UpdateStatus.NeedsUpdate
) : UserDataHolderBase(), CargoProject {
    override val project get() = projectService.project

    override val workspace: CargoWorkspace? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val rawWorkspace = rawWorkspace ?: return@lazy null
        val stdlib = stdlib ?: return@lazy if (!userDisabledFeatures.isEmpty() && isUnitTestMode) {
            rawWorkspace.withDisabledFeatures(userDisabledFeatures)
        } else {
            rawWorkspace
        }
        rawWorkspace.withStdlib(stdlib, rawWorkspace.cfgOptions, rustcInfo)
            .withDisabledFeatures(userDisabledFeatures)
    }

    override val presentableName: String by lazy {
        workspace?.packages?.singleOrNull {
            it.origin == PackageOrigin.WORKSPACE && it.rootDirectory == workingDirectory
        }?.name ?: workingDirectory.fileName.toString()
    }

    private val rootDirCache = AtomicReference<VirtualFile>()
    override val rootDir: VirtualFile?
        get() {
            val cached = rootDirCache.get()
            if (cached != null && cached.isValid) return cached
            val file = LocalFileSystem.getInstance().findFileByIoFile(workingDirectory.toFile())
            rootDirCache.set(file)
            return file
        }

    override val workspaceRootDir: VirtualFile? get() = rawWorkspace?.workspaceRoot

    @TestOnly
    fun setRootDir(dir: VirtualFile) = rootDirCache.set(dir)

    // Checks that the project is https://github.com/rust-lang/rust
    fun doesProjectLooksLikeRustc(): Boolean {
        val workspace = rawWorkspace ?: return false
        // "rustc" package was renamed to "rustc_middle" in https://github.com/rust-lang/rust/pull/70536
        // so starting with rustc 1.42 a stable way to identify it is to try to find any of some possible packages
        val possiblePackages = listOf("rustc", "rustc_middle", "rustc_typeck")
        return workspace.findPackageByName(AutoInjectedCrates.STD) != null &&
            workspace.findPackageByName(AutoInjectedCrates.CORE) != null &&
            possiblePackages.any { workspace.findPackageByName(it) != null }
    }

    fun withStdlib(result: TaskResult<StandardLibrary>): CargoProjectImpl = when (result) {
        is TaskResult.Ok -> copy(stdlib = result.value, stdlibStatus = UpdateStatus.UpToDate)
        is TaskResult.Err -> copy(stdlibStatus = UpdateStatus.UpdateFailed(result.reason))
    }

    fun withWorkspace(result: TaskResult<CargoWorkspace>): CargoProjectImpl = when (result) {
        is TaskResult.Ok -> copy(
            rawWorkspace = result.value,
            workspaceStatus = UpdateStatus.UpToDate,
            userDisabledFeatures = userDisabledFeatures.retain(result.value.packages)
        )
        is TaskResult.Err -> copy(workspaceStatus = UpdateStatus.UpdateFailed(result.reason))
    }

    fun withRustcInfo(result: TaskResult<RustcInfo>): CargoProjectImpl = when (result) {
        is TaskResult.Ok -> copy(rustcInfo = result.value, rustcInfoStatus = UpdateStatus.UpToDate)
        is TaskResult.Err -> copy(rustcInfoStatus = UpdateStatus.UpdateFailed(result.reason))
    }

    override fun toString(): String =
        "CargoProject(manifest = $manifest)"
}

val CargoProjectsService.allPackages: Sequence<CargoWorkspace.Package>
    get() = allProjects.asSequence().mapNotNull { it.workspace }.flatMap { it.packages.asSequence() }

val CargoProjectsService.allTargets: Sequence<CargoWorkspace.Target>
    get() = allPackages.flatMap { it.targets.asSequence() }

private fun hasAtLeastOneValidProject(projects: Collection<CargoProject>) =
    projects.any { it.manifest.exists() }

/** Keep in sync with [org.rust.cargo.project.model.impl.deduplicateProjects] */
private fun isExistingProject(projects: Collection<CargoProject>, manifest: Path): Boolean {
    if (projects.any { it.manifest == manifest }) return true
    return projects.mapNotNull { it.workspace }.flatMap { it.packages }
        .filter { it.origin == PackageOrigin.WORKSPACE }
        .any { it.rootDirectory == manifest.parent }
}

private fun doRefresh(project: Project, projects: List<CargoProjectImpl>): CompletableFuture<List<CargoProjectImpl>> {
    @Suppress("UnstableApiUsage")
    if (!project.isTrusted()) return CompletableFuture.completedFuture(projects)
    // TODO: get rid of `result` here
    val result = if (projects.isEmpty()) {
        CompletableFuture.completedFuture(emptyList())
    } else {
        val result = CompletableFuture<List<CargoProjectImpl>>()
        val syncTask = CargoSyncTask(project, projects, result)
        project.taskQueue.run(syncTask)
        result
    }

    return result.thenApply { updatedProjects ->
        runWithNonLightProject(project) {
            setupProjectRoots(project, updatedProjects)
        }
        updatedProjects
    }
}

private inline fun runWithNonLightProject(project: Project, action: () -> Unit) {
    if ((project as? ProjectEx)?.isLight != true) {
        action()
    } else {
        check(isUnitTestMode)
    }
}

private fun setupProjectRoots(project: Project, cargoProjects: List<CargoProject>) {
    invokeAndWaitIfNeeded {
        // Initialize services that we use (probably indirectly) in write action below.
        // Otherwise, they can be initialized in write action that may lead to deadlock
        RunManager.getInstance(project)
        ProjectFileIndex.getInstance(project)

        runWriteAction {
            if (project.isDisposed) return@runWriteAction
            ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring {
                for (cargoProject in cargoProjects) {
                    cargoProject.workspaceRootDir?.setupContentRoots(project) { contentRoot ->
                        addExcludeFolder(FileUtil.join(contentRoot.url, CargoConstants.ProjectLayout.target))
                    }

                    if ((cargoProject as? CargoProjectImpl)?.doesProjectLooksLikeRustc() == true) {
                        cargoProject.workspaceRootDir?.setupContentRoots(project) { contentRoot ->
                            addExcludeFolder(FileUtil.join(contentRoot.url, "build"))
                        }
                    }

                    val workspacePackages = cargoProject.workspace?.packages
                        .orEmpty()
                        .filter { it.origin == PackageOrigin.WORKSPACE }

                    for (pkg in workspacePackages) {
                        pkg.contentRoot?.setupContentRoots(project, ContentEntry::setup)
                    }
                }
            }
        }
    }
}

private fun VirtualFile.setupContentRoots(project: Project, setup: ContentEntry.(VirtualFile) -> Unit) {
    val packageModule = ModuleUtilCore.findModuleForFile(this, project) ?: return
    setupContentRoots(packageModule, setup)
}

private fun VirtualFile.setupContentRoots(packageModule: Module, setup: ContentEntry.(VirtualFile) -> Unit) {
    ModuleRootModificationUtil.updateModel(packageModule) { rootModel ->
        rootModel.contentEntries.singleOrNull()?.setup(this)
    }
}

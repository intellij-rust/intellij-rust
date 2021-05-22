/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapiext.Testmark
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.rust.RsTask
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsPsiTreeChangeEvent.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.indexes.RsMacroCallIndex
import org.rust.lang.core.resolve2.defMapService
import org.rust.lang.core.resolve2.resolveToMacroWithoutPsi
import org.rust.openapiext.*
import org.rust.stdext.*
import org.rust.taskQueue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

interface MacroExpansionManager {
    val indexableDirectory: VirtualFile?
    fun getExpansionFor(call: RsPossibleMacroCall): CachedValueProvider.Result<MacroExpansion?>
    fun getExpandedFrom(element: RsExpandedElement): RsPossibleMacroCall?
    /** Optimized equivalent for `getExpandedFrom(element)?.context` */
    fun getContextOfMacroCallExpandedFrom(stubParent: RsFile): PsiElement?
    fun isExpansionFileOfCurrentProject(file: VirtualFile): Boolean
    fun reexpand()

    val macroExpansionMode: MacroExpansionMode

    var expansionState: ExpansionState?

    @TestOnly
    fun setUnitTestExpansionModeAndDirectory(mode: MacroExpansionScope, cacheDirectory: String = ""): Disposable

    data class ExpansionState(
        val expandedSearchScope: GlobalSearchScope,
        val stepModificationTracker: ModificationTracker
    )

    companion object {
        @JvmStatic
        fun isExpansionFile(file: VirtualFile): Boolean =
            file.fileSystem == MacroExpansionFileSystem.getInstance()

        @JvmStatic
        fun invalidateCaches() {
            getCorruptionMarkerFile().apply {
                parent?.createDirectories()
                Files.createFile(this)
            }
        }

        @Synchronized
        fun checkInvalidatedStorage() {
            if (getCorruptionMarkerFile().exists()) {
                try {
                    getBaseMacroDir().cleanDirectory()
                } catch (e: IOException) {
                    MACRO_LOG.warn(e)
                }
            }
        }
    }
}

inline fun <T> MacroExpansionManager.withExpansionState(
    newState: MacroExpansionManager.ExpansionState,
    action: () -> T
): T {
    val oldState = expansionState
    expansionState = newState
    return try {
        action()
    } finally {
        expansionState = oldState
    }
}

@JvmField
val MACRO_LOG: Logger = Logger.getInstance("org.rust.macros")

// The path is visible in indexation progress
const val MACRO_EXPANSION_VFS_ROOT = "rust_expanded_macros"
private const val CORRUPTION_MARKER_NAME = "corruption.marker"

fun getBaseMacroDir(): Path =
    RsPathManager.pluginDirInSystem().resolve("macros")

private fun getCorruptionMarkerFile(): Path =
    getBaseMacroDir().resolve(CORRUPTION_MARKER_NAME)

@State(name = "MacroExpansionManager", storages = [
    Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED),
    Storage("misc.xml", roamingType = RoamingType.DISABLED, deprecated = true)
])
class MacroExpansionManagerImpl(
    val project: Project
) : MacroExpansionManager,
    PersistentStateComponent<MacroExpansionManagerImpl.PersistentState>,
    com.intellij.configurationStore.SettingsSavingComponent,
    Disposable {

    data class PersistentState(var directoryName: String? = null)

    private var dirs: Dirs? = null

    // Guarded by the platform RWLock. Assigned only once
    private var inner: MacroExpansionServiceImplInner? = null

    @Volatile
    private var isDisposed: Boolean = false

    override fun getState(): PersistentState {
        return PersistentState(dirs?.projectDirName)
    }

    override suspend fun save() {
        inner?.save()
    }

    override fun loadState(state: PersistentState) {
        // initialized manually at setUnitTestExpansionModeAndDirectory
        if (isUnitTestMode) return

        val dirs = updateDirs(state.directoryName)
        this.dirs = dirs

        MACRO_LOG.debug("Loading MacroExpansionManager")

        ApplicationManager.getApplication().executeOnPooledThread(Runnable {
            val preparedImpl = MacroExpansionServiceBuilder.prepare(dirs)
            MACRO_LOG.debug("Loading MacroExpansionManager - data loaded")

            val impl = runReadAction {
                if (isDisposed) return@runReadAction null
                preparedImpl.buildInReadAction(project)
            } ?: return@Runnable

            MACRO_LOG.debug("Loading MacroExpansionManager - deserialized")

            invokeLater {
                runWriteAction {
                    if (isDisposed) return@runWriteAction
                    // Publish `inner` in the same write action where `stateLoaded` is called.
                    // The write action also makes it synchronized with `CargoProjectsService.initialized`
                    inner = impl
                    impl.stateLoaded(this)
                }
            }
        })
    }

    override fun noStateLoaded() {
        loadState(PersistentState(null))
    }

    override val indexableDirectory: VirtualFile?
        get() = inner?.expansionsDirVi

    override fun getExpansionFor(call: RsPossibleMacroCall): CachedValueProvider.Result<MacroExpansion?> {
        val impl = inner
        return when {
            call is RsMacroCall && call.macroName == "include" -> expandIncludeMacroCall(call)
            impl != null -> impl.getExpansionFor(call)
            isUnitTestMode && call is RsMacroCall -> expandMacroOld(call)
            else -> CachedValueProvider.Result.create(null, project.rustStructureModificationTracker)
        }
    }

    private fun expandIncludeMacroCall(call: RsMacroCall): CachedValueProvider.Result<MacroExpansion?> {
        val expansion = run {
            val includingFile = call.findIncludingFile() ?: return@run null
            val items = includingFile.stubChildrenOfType<RsExpandedElement>()
            MacroExpansion.Items(includingFile, items)
        }
        return CachedValueProvider.Result.create(expansion, call.rustStructureOrAnyPsiModificationTracker)
    }

    override fun getExpandedFrom(element: RsExpandedElement): RsPossibleMacroCall? {
        // For in-memory expansions
        element.getUserData(RS_EXPANSION_MACRO_CALL)?.let { return it }

        val inner = inner
        return if (inner != null && inner.isExpansionModeNew) {
            inner.getExpandedFrom(element)
        } else {
            null
        }
    }

    override fun getContextOfMacroCallExpandedFrom(stubParent: RsFile): PsiElement? {
        val inner = inner
        return if (inner != null && inner.isExpansionModeNew) {
            inner.getContextOfMacroCallExpandedFrom(stubParent)
        } else {
            null
        }
    }

    override fun isExpansionFileOfCurrentProject(file: VirtualFile): Boolean =
        inner?.isExpansionFileOfCurrentProject(file) == true

    override fun reexpand() {
        inner?.reexpand()
    }

    override val macroExpansionMode: MacroExpansionMode
        get() = inner?.expansionMode ?: MacroExpansionMode.OLD

    override var expansionState: MacroExpansionManager.ExpansionState?
        get() = inner?.expansionState
        set(value) {
            inner?.expansionState = value
        }

    override fun setUnitTestExpansionModeAndDirectory(mode: MacroExpansionScope, cacheDirectory: String): Disposable {
        check(isUnitTestMode)
        val dir = updateDirs(if (cacheDirectory.isNotEmpty()) cacheDirectory else null)
        val impl = MacroExpansionServiceBuilder.prepare(dir).buildInReadAction(project)
        this.dirs = dir
        this.inner = impl
        impl.macroExpansionMode = mode
        val saveCacheOnDispose = cacheDirectory.isNotEmpty()
        val disposable = impl.setupForUnitTests(saveCacheOnDispose)
        Disposer.register(disposable) {
            this.inner = null
            this.dirs = null
        }
        return disposable
    }

    override fun dispose() {
        inner?.dispose()
        isDisposed = true
    }

    object Testmarks {
        val stubBasedRefMatch = Testmark("stubBasedRefMatch")
        val stubBasedLookup = Testmark("stubBasedLookup")
        val refsRecover = Testmark("refsRecover")
        val refsRecoverExactHit = Testmark("refsRecoverExactHit")
        val refsRecoverCallHit = Testmark("refsRecoverCallHit")
        val refsRecoverNotHit = Testmark("refsRecoverNotHit")
    }
}

private fun updateDirs(projectDirName: String?): Dirs {
    return updateDirs0(projectDirName ?: randomLowercaseAlphabetic(8))
}

private fun updateDirs0(projectDirName: String): Dirs {
    val baseProjectDir = getBaseMacroDir()
        .also { it.createDirectories() }
    return Dirs(
        baseProjectDir.resolve("$projectDirName.dat"),
        projectDirName
    )
}

private data class Dirs(
    val dataFile: Path,
    val projectDirName: String
) {
    // Path in the MacroExpansionVFS
    val expansionDirPath: String get() = "/$MACRO_EXPANSION_VFS_ROOT/$projectDirName"
}

private class MacroExpansionServiceBuilder private constructor(
    private val dirs: Dirs,
    private val serStorage: SerializedExpandedMacroStorage?,
    private val expansionsDirVi: VirtualFile
) {
    fun buildInReadAction(project: Project): MacroExpansionServiceImplInner {
        val storage = serStorage?.deserializeInReadAction(project) ?: ExpandedMacroStorage(project)
        return MacroExpansionServiceImplInner(project, dirs, storage, expansionsDirVi)
    }

    companion object {
        fun prepare(dirs: Dirs): MacroExpansionServiceBuilder {
            val dataFile = dirs.dataFile
            MacroExpansionManager.checkInvalidatedStorage()
            MacroExpansionFileSystemRootsLoader.loadProjectDirs()
            val loaded = load(dataFile)

            val vfs = MacroExpansionFileSystem.getInstance()
            val serStorage = loaded?.first
            val loadedFsDir = loaded?.second

            if (loadedFsDir != null) {
                vfs.setDirectory(dirs.expansionDirPath, loadedFsDir)
            } else {
                MACRO_LOG.debug("Using fresh ExpandedMacroStorage")
                vfs.createDirectoryIfNotExistsOrDummy(dirs.expansionDirPath)
            }

            val expansionsDirVi = vfs.refreshAndFindFileByPath(dirs.expansionDirPath)
                ?: error("Impossible because the directory is just created; ${dirs.expansionDirPath}")

            return MacroExpansionServiceBuilder(dirs, serStorage, expansionsDirVi)
        }

        private fun load(dataFile: Path): kotlin.Pair<SerializedExpandedMacroStorage, MacroExpansionFileSystem.FSItem.FSDir>? {
            return try {
                dataFile.newInflaterDataInputStream().use { data ->
                    val sems = SerializedExpandedMacroStorage.load(data) ?: return null
                    val fs = MacroExpansionFileSystem.readFSItem(data, null) as? MacroExpansionFileSystem.FSItem.FSDir
                        ?: return null
                    sems to fs
                }
            } catch (e: java.nio.file.NoSuchFileException) {
                null
            } catch (e: Exception) {
                MACRO_LOG.warn(e)
                null
            }
        }
    }
}

/** See [MacroExpansionFileSystem] docs for explanation of what happens here */
private object MacroExpansionFileSystemRootsLoader {
    @Synchronized
    fun loadProjectDirs() {
        val vfs = MacroExpansionFileSystem.getInstance()
        if (!vfs.exists("/$MACRO_EXPANSION_VFS_ROOT")) {
            val root = MacroExpansionFileSystem.FSItem.FSDir(null, MACRO_EXPANSION_VFS_ROOT)
            try {
                val dirs = getProjectListDataFile().newInflaterDataInputStream().use { data ->
                    val count = data.readInt()
                    (0 until count).map {
                        val name = data.readUTF()
                        val ts = data.readLong()
                        MacroExpansionFileSystem.FSItem.FSDir.DummyDir(root, name, ts)
                    }
                }

                for (dir in dirs) {
                    root.addChild(dir)
                }
            } catch (ignored: java.nio.file.NoSuchFileException) {
            } catch (e: Exception) {
                MACRO_LOG.warn(e)
            } finally {
                vfs.setDirectory("/$MACRO_EXPANSION_VFS_ROOT", root, override = false)
            }
        }
    }

    fun saveProjectDirs() {
        val root = MacroExpansionFileSystem.getInstance().getDirectory("/$MACRO_EXPANSION_VFS_ROOT") ?: return
        val dataFile = getProjectListDataFile()
        Files.createDirectories(dataFile.parent)
        dataFile.newDeflaterDataOutputStream().use { data ->
            val children = root.copyChildren()
            data.writeInt(children.size)
            for (dir in children) {
                data.writeUTF(dir.name)
                data.writeLong(dir.timestamp)
            }
        }
    }

    private fun getProjectListDataFile(): Path = getBaseMacroDir().resolve("project_list.dat")
}

private class MacroExpansionServiceImplInner(
    private val project: Project,
    val dirs: Dirs,
    private val storage: ExpandedMacroStorage,
    val expansionsDirVi: VirtualFile
) {
    @Volatile
    private var lastSavedStorageModCount: Long = storage.modificationTracker.modificationCount

    /**
     * We must use a separate pool because:
     * 1. [ForkJoinPool.commonPool] is heavily used by the platform
     * 2. [ForkJoinPool] can start execute a task when joining ([ForkJoinTask.get]) another task
     * 3. the platform sometimes join ForkJoinTasks under read lock
     * 4. for macro expansion it's critically important that tasks are executed without read lock.
     *
     * In short, use of [ForkJoinPool.commonPool] in this place leads to crashes.
     * See [issue](https://github.com/intellij-rust/intellij-rust/issues/3966)
     */
    private val pool = Executors.newWorkStealingPool()

    private val stepModificationTracker: SimpleModificationTracker = SimpleModificationTracker()

    private val dataFile: Path
        get() = dirs.dataFile

    @TestOnly
    var macroExpansionMode: MacroExpansionScope = MacroExpansionScope.NONE

    var expansionState: MacroExpansionManager.ExpansionState? by ThreadLocalDelegate { null }

    fun isExpansionFileOfCurrentProject(file: VirtualFile): Boolean {
        return VfsUtil.isAncestor(expansionsDirVi, file, true)
    }

    suspend fun save() {
        if (lastSavedStorageModCount == storage.modificationTracker.modificationCount) return

        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) { // ensure dispatcher knows we are doing blocking IO
            // Using a buffer to avoid IO in the read action
            // BACKCOMPAT: 2020.1 use async read action and extract `runReadAction` from `withContext`
            val (buffer, modCount) = runReadAction {
                val buffer = BufferExposingByteArrayOutputStream(1024 * 1024) // average stdlib storage size
                DataOutputStream(buffer).use { data ->
                    ExpandedMacroStorage.saveStorage(storage, data)
                    val dirToSave = MacroExpansionFileSystem.getInstance().getDirectory(dirs.expansionDirPath) ?: run {
                        MACRO_LOG.warn("Expansion directory does not exist when saving the component: ${dirs.expansionDirPath}")
                        MacroExpansionFileSystem.FSItem.FSDir(null, dirs.projectDirName)
                    }
                    MacroExpansionFileSystem.writeFSItem(data, dirToSave)
                }
                buffer to storage.modificationTracker.modificationCount
            }

            Files.createDirectories(dataFile.parent)
            dataFile.newDeflaterDataOutputStream().use { it.write(buffer.internalBuffer) }
            MacroExpansionFileSystemRootsLoader.saveProjectDirs()
            lastSavedStorageModCount = modCount
        }
    }

    fun dispose() {
        // Can be invoked in heavy tests (e.g. RsRealProjectAnalysisTest)
        if (!isUnitTestMode) {
            releaseExpansionDirectory()
        }
    }

    private fun releaseExpansionDirectory() {
        val vfs = MacroExpansionFileSystem.getInstanceOrNull() ?: return // null means plugin unloading

        // See [MacroExpansionFileSystem] docs for explanation of what happens here
        RefreshQueue.getInstance().refresh(/*async = */ !isUnitTestMode, /*recursive = */ true, {
            vfs.makeDummy(dirs.expansionDirPath)
        }, listOf(expansionsDirVi))
    }

    private var performConsistencyCheckBeforeTask: Boolean = true

    private fun submitTask(task: Task.Backgroundable) {
        project.taskQueue.run(task)
    }

    @Synchronized
    private fun checkStorageConsistencyOrClearMacrosDirectoryIfNeeded() {
        if (performConsistencyCheckBeforeTask) {
            performConsistencyCheckBeforeTask = false
            checkStorageConsistencyOrClearMacrosDirectory()
        }
    }

    private fun checkStorageConsistencyOrClearMacrosDirectory() {
        if (storage.isEmpty || !isExpansionModeNew) {
            cleanMacrosDirectoryAndStorage()
        } else {
            checkStorageConsistency()
        }
    }

    private fun cleanMacrosDirectoryAndStorage() {
        performConsistencyCheckBeforeTask = false
        submitTask(object : Task.Backgroundable(project, "Cleaning outdated macros", false), RsTask {
            override fun run(indicator: ProgressIndicator) {
                if (!isUnitTestMode) checkReadAccessNotAllowed()
                val vfs = MacroExpansionFileSystem.getInstance()
                vfs.cleanDirectoryIfExists(dirs.expansionDirPath)
                vfs.createDirectoryIfNotExistsOrDummy(dirs.expansionDirPath)
                dirs.dataFile.delete()
                WriteAction.runAndWait<Throwable> {
                    VfsUtil.markDirtyAndRefresh(false, true, true, expansionsDirVi)
                    storage.clear()
                    if (!project.isDisposed) {
                        project.rustPsiManager.incRustStructureModificationCount()
                    }
                }
            }

            override val runSyncInUnitTests: Boolean
                get() = true

            override val taskType: RsTask.TaskType
                get() = RsTask.TaskType.MACROS_CLEAR
        })
    }

    private fun checkStorageConsistency() {
        performConsistencyCheckBeforeTask = false
        submitTask(object : Task.Backgroundable(project, "Cleaning outdated macros", false) {
            override fun run(indicator: ProgressIndicator) {
                checkReadAccessNotAllowed()

                val duration = measureTimeMillis {
                    refreshExpansionDirectory()
                    findAndDeleteLeakedExpansionFiles()
                    findAndRemoveInvalidExpandedMacroInfosFromStorage()
                }

                MACRO_LOG.info("Done consistency check in $duration ms")
            }

            private fun refreshExpansionDirectory() {
                check(expansionsDirVi.isValid)
                VfsUtil.markDirtyAndRefresh(false, true, false, expansionsDirVi)
            }

            private fun findAndDeleteLeakedExpansionFiles() {
                val toDelete = mutableListOf<VirtualFile>()
                runReadAction {
                    VfsUtil.iterateChildrenRecursively(expansionsDirVi, null) { file ->
                        if (!file.isValidExpansionFile()) {
                            toDelete += file
                        }
                        true
                    }
                }
                if (toDelete.isNotEmpty()) {
                    val batch = VfsBatch()
                    toDelete.forEach { batch.deleteFile(it) }
                    WriteAction.runAndWait<Throwable> {
                        batch.applyToVfs(async = false)
                    }
                }
            }

            private fun VirtualFile.isValidExpansionFile(): Boolean {
                if (isDirectory) return true
                val info = storage.getInfoForExpandedFile(this)
                return if (info == null) {
                    false
                } else {
                    when (val result = VfsInternals.getUpToDateContentHash(this)) {
                        VfsInternals.ContentHashResult.Disabled -> true // Skip the check if hashes are disabled
                        is VfsInternals.ContentHashResult.Ok -> {
                            info.expansionFileHash == result.hash.getLeading64bits()
                        }
                        is VfsInternals.ContentHashResult.Err -> {
                            // See `MacroExpansionFileSystem.contentsToByteArray`
                            MACRO_LOG.warn(result.error)
                            false
                        }
                    }
                }
            }

            private fun findAndRemoveInvalidExpandedMacroInfosFromStorage() {
                val toRemove = mutableListOf<ExpandedMacroInfo>()
                runReadAction {
                    storage.processExpandedMacroInfos { info ->
                        val expansionFile = info.expansionFile
                        if (expansionFile != null && !expansionFile.isValid) {
                            toRemove.add(info)
                        }
                    }
                }
                if (toRemove.isNotEmpty()) {
                    WriteAction.runAndWait<Throwable> {
                        toRemove.forEach {
                            storage.removeInvalidInfo(it, true)
                            it.sourceFile.markForRebind()
                        }
                    }
                }
            }
        })
    }

    fun stateLoaded(parentDisposable: Disposable) {
        check(!isUnitTestMode) // initialized manually at setUnitTestExpansionModeAndDirectory
        checkWriteAccessAllowed()

        setupListeners(parentDisposable)
        deleteOldExpansionDir()

        val cargoProjects = project.cargoProjects
        when {
            !cargoProjects.initialized -> {
                // Do nothing. If `CargoProjectService` is not initialized yet, it will make
                // roots change at the end of initialization (at the same write action where
                // `initialized = true` is assigned) and `processUnprocessedMacros` will be
                // triggered by `CargoProjectsService.CARGO_PROJECTS_TOPIC`
                MACRO_LOG.debug("Loading MacroExpansionManager finished - no events fired")
            }
            !cargoProjects.hasAtLeastOneValidProject -> {
                // `CargoProjectService` is already initialized, but there are no Rust projects.
                // No projects - no macros
                cleanMacrosDirectoryAndStorage()
                MACRO_LOG.debug("Loading MacroExpansionManager finished - no rust projects")
            }
            else -> {
                // `CargoProjectService` is already initialized and there are Rust projects.
                // Make roots change in order to refresh [RsIndexableSetContributor]
                // which value is changed after after `inner` assigning
                ProjectRootManagerEx.getInstanceEx(project)
                    .makeRootsChange(EmptyRunnable.getInstance(), false, true)

                processUnprocessedMacros()

                MACRO_LOG.debug("Loading MacroExpansionManager finished - roots change fired")
            }
        }
    }

    private fun setupListeners(disposable: Disposable) {
        val treeChangeListener = ChangedMacroUpdater()
        PsiManager.getInstance(project).addPsiTreeChangeListener(treeChangeListener, disposable)
        ApplicationManager.getApplication().addApplicationListener(treeChangeListener, disposable)

        val connect = project.messageBus.connect(disposable)

        connect.subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, CargoProjectsListener { _, _ ->
            settingsChanged()
        })

        connect.subscribe(RustProjectSettingsService.RUST_SETTINGS_TOPIC, object : RustProjectSettingsService.RustSettingsListener {
            override fun rustSettingsChanged(e: RustProjectSettingsService.RustSettingsChangedEvent) {
                if (!e.affectsCargoMetadata) { // if affect cargo metadata, will be invoked by CARGO_PROJECTS_TOPIC
                    if (e.isChanged(RustProjectSettingsService.State::macroExpansionEngine)) {
                        settingsChanged()
                    }
                }
            }
        })

        project.rustPsiManager.subscribeRustPsiChange(connect, treeChangeListener)
    }

    // Previous plugin versions stored expansion to this directory
    // TODO remove it someday
    private fun deleteOldExpansionDir() {
        val oldDirPath = Paths.get(PathManager.getSystemPath()).resolve("rust_expanded_macros")
        if (oldDirPath.exists()) {
            oldDirPath.delete()
            val oldDirVFile = LocalFileSystem.getInstance().findFileByIoFile(oldDirPath.toFile())
            if (oldDirVFile != null) {
                VfsUtil.markDirtyAndRefresh(true, true, true, oldDirVFile)
            }
        }
    }

    private fun settingsChanged() {
        if (!isExpansionModeNew && !storage.isEmpty) {
            cleanMacrosDirectoryAndStorage()
        }
        processUnprocessedMacros()
    }

    private enum class ChangedMacrosScope { NONE, WORKSPACE, ALL }

    private operator fun ChangedMacrosScope.plus(other: ChangedMacrosScope): ChangedMacrosScope =
        if (ordinal > other.ordinal) this else other

    private inner class ChangedMacroUpdater : RsPsiTreeChangeAdapter(),
                                              RustPsiChangeListener,
                                              ApplicationListener {

        private var shouldProcessChangedMacrosOnWriteActionFinish: ChangedMacrosScope = ChangedMacrosScope.NONE

        override fun handleEvent(event: RsPsiTreeChangeEvent) {
            if (!isExpansionModeNew) return
            val file = event.file as? RsFile ?: return
            if (RsPsiManager.isIgnorePsiEvents(file)) return
            val virtualFile = file.virtualFile ?: return
            if (virtualFile !is VirtualFileWithId) return

            if (file.treeElement == null) return

            if (event is ChildrenChange.Before && event.isGenericChange) {
                storage.getSourceFile(file.virtualFile)?.switchToStrongRefsTemporary()
            }

            val element = when (event) {
                is ChildAddition.After -> event.child
                is ChildReplacement.After -> event.newChild
                is ChildrenChange.After -> if (!event.isGenericChange) event.parent else return
                else -> return
            }

            // Handle attribute rename `#[foo]` -> `#[bar]`
            val parentOrSelf = element.ancestorOrSelf<RsMetaItem>() ?: element

            val macroCalls = parentOrSelf.descendantsOfTypeOrSelf<RsPossibleMacroCall>()
            if (macroCalls.isNotEmpty()) {
                val sf = storage.getOrCreateSourceFile(virtualFile) ?: return
                sf.newMacroCallsAdded(macroCalls)
                if (!MacroExpansionManager.isExpansionFile(virtualFile)) {
                    scheduleChangedMacrosUpdate(file.isWorkspaceMember())
                }
            }
        }

        override fun rustPsiChanged(file: PsiFile, element: PsiElement, isStructureModification: Boolean) {
            if (!isExpansionModeNew) return
            val shouldScheduleUpdate =
                (isStructureModification || element.ancestorOrSelf<RsPossibleMacroCall>()?.isTopLevelExpansion == true
                    || RsProcMacroPsiUtil.canBeInProcMacroCallBody(element)) &&
                    file.virtualFile?.let { MacroExpansionManager.isExpansionFile(it) } == false
            if (shouldScheduleUpdate && file is RsFile) {
                val isWorkspace = file.isWorkspaceMember()
                scheduleChangedMacrosUpdate(isWorkspace)
                project.defMapService.onFileChanged(file)
            }
        }

        override fun writeActionFinished(action: Any) {
            when (shouldProcessChangedMacrosOnWriteActionFinish) {
                ChangedMacrosScope.NONE -> Unit
                ChangedMacrosScope.WORKSPACE -> processChangedMacros(true)
                ChangedMacrosScope.ALL -> processChangedMacros(false)
            }
            shouldProcessChangedMacrosOnWriteActionFinish = ChangedMacrosScope.NONE
        }

        private fun scheduleChangedMacrosUpdate(workspaceOnly: Boolean) {
            shouldProcessChangedMacrosOnWriteActionFinish += if (workspaceOnly) ChangedMacrosScope.WORKSPACE else ChangedMacrosScope.ALL
        }
    }

    private fun RsFile.isWorkspaceMember(): Boolean {
        // Must be dumb-aware
        val pkg = project.cargoProjects.findPackageForFile(virtualFile ?: return false)
        return pkg?.origin == PackageOrigin.WORKSPACE
    }

    fun reexpand() {
        cleanMacrosDirectoryAndStorage()
        processUnprocessedMacros()
    }

    val expansionMode: MacroExpansionMode
        get() {
            return if (isUnitTestMode) {
                MacroExpansionMode.New(macroExpansionMode)
            } else {
                project.rustSettings.macroExpansionEngine.toMode()
            }
        }

    val isExpansionModeNew: Boolean
        get() = expansionMode is MacroExpansionMode.New

    private fun vfsBatchFactory(): MacroExpansionVfsBatch {
        return MacroExpansionVfsBatchImpl(dirs.projectDirName)
    }

    private fun createExpandedSearchScope(step: Int): GlobalSearchScope {
        val expansionDirs = (0 until step).mapNotNull {
            expansionsDirVi.findChild(it.toString())
        }
        val expansionScope = GlobalSearchScopes.directoriesScope(project, true, *expansionDirs.toTypedArray())
        return GlobalSearchScope.allScope(project).uniteWith(expansionScope)
    }

    private fun processUnprocessedMacros() {
        MACRO_LOG.info("processUnprocessedMacros")
        checkStorageConsistencyOrClearMacrosDirectoryIfNeeded()
        if (!isExpansionModeNew) return
        class ProcessUnprocessedMacrosTask : MacroExpansionTaskBase(
            project,
            storage,
            pool,
            ::vfsBatchFactory,
            ::createExpandedSearchScope,
            stepModificationTracker
        ) {
            override fun getMacrosToExpand(dumbService: DumbService): Sequence<List<Extractable>> {
                val mode = expansionMode

                val scope = when (mode.toScope()) {
                    MacroExpansionScope.ALL -> GlobalSearchScope.allScope(project)
                    MacroExpansionScope.WORKSPACE -> GlobalSearchScope.projectScope(project)
                    MacroExpansionScope.NONE -> return emptySequence() // GlobalSearchScope.EMPTY_SCOPE
                }

                val calls = runReadActionInSmartMode(dumbService) {
                    val calls = RsMacroCallIndex.getMacroCalls(project, scope)
                        .filter { it.isTopLevelExpansion }
                    MACRO_LOG.info("Macros to expand: ${calls.size}")
                    calls.groupBy { it.containingFile.virtualFile }

                }
                return storage.makeExpansionTask(calls)
            }

            override val taskType: RsTask.TaskType get() = RsTask.TaskType.MACROS_UNPROCESSED
        }
        submitTask(ProcessUnprocessedMacrosTask())
    }

    private fun processChangedMacros(workspaceOnly: Boolean) {
        MACRO_LOG.info("processChangedMacros")
        checkStorageConsistencyOrClearMacrosDirectoryIfNeeded()
        if (!isExpansionModeNew) return

        // Fixes inplace rename when the renamed element is referenced from a macro call body
        if (isTemplateActiveInAnyEditor()) return

        class ProcessModifiedMacrosTask : MacroExpansionTaskBase(
            project,
            storage,
            pool,
            ::vfsBatchFactory,
            ::createExpandedSearchScope,
            stepModificationTracker
        ) {
            override fun getMacrosToExpand(dumbService: DumbService): Sequence<List<Extractable>> {
                return runReadAction { storage.makeValidationTask(workspaceOnly) }
            }

            override val taskType: RsTask.TaskType
                get() = if (workspaceOnly) RsTask.TaskType.MACROS_WORKSPACE else RsTask.TaskType.MACROS_FULL

            override val progressBarShowDelay: Int get() = 2000
        }

        val task = ProcessModifiedMacrosTask()

        submitTask(task)
    }

    private fun isTemplateActiveInAnyEditor(): Boolean {
        val tm = TemplateManager.getInstance(project)
        for (editor in FileEditorManager.getInstance(project).allEditors) {
            if (editor is TextEditor && tm.getActiveTemplate(editor.editor) != null) return true
        }

        return false
    }

    fun getExpansionFor(call: RsPossibleMacroCall): CachedValueProvider.Result<MacroExpansion?> {
        val expansionState = expansionState

        if (expansionMode == MacroExpansionMode.OLD) {
            if (expansionState != null) return nullResult()
            if (call !is RsMacroCall) return nullResult()
            return expandMacroOld(call)
        }

        val containingFile: VirtualFile? = call.containingFile.virtualFile

        if (!call.isTopLevelExpansion || containingFile?.fileSystem?.isSupportedFs != true) {
            if (expansionState != null) return nullResult()
            if (call !is RsMacroCall) return nullResult()
            return expandMacroToMemoryFile(call, storeRangeMap = true)
        }

        // Forbid accessing expansions of next steps
        if (expansionState != null && !expansionState.expandedSearchScope.contains(containingFile)) {
            return nullResult()
        }

        val expansion = storage.getInfoForCall(call)?.getExpansion()
        return if (call is RsMacroCall) {
            CachedValueProvider.Result.create(expansion, storage.modificationTracker, call.modificationTracker)
        } else {
            CachedValueProvider.Result.create(expansion, call.rustStructureOrAnyPsiModificationTracker)
        }
    }

    private fun nullResult(): CachedValueProvider.Result<MacroExpansion?> =
        CachedValueProvider.Result.create(null, ModificationTracker.EVER_CHANGED)

    fun getExpandedFrom(element: RsExpandedElement): RsPossibleMacroCall? {
        checkReadAccessAllowed()
        val parent = element.stubParent
        if (parent is RsFile) {
            val file = parent.virtualFile ?: return null
            if (file is VirtualFileWithId) {
                return storage.getInfoForExpandedFile(file)?.getMacroCall()
            }
        }

        return null
    }

    /**
     * Optimized equivalent for `getExpandedFrom(element)?.context`
     * TODO a comment about proc macros
     */
    fun getContextOfMacroCallExpandedFrom(stubParent: RsFile): PsiElement? {
        val (macroCall, parent) = getContextOfMacroCallExpandedFromInner(stubParent) ?: return null
        return when (macroCall) {
            is RsMacroCall -> parent
            is RsMetaItem -> macroCall.owner?.context
            else -> null
        }
    }

    fun getContextOfMacroCallExpandedFromInner(stubParent: RsFile): kotlin.Pair<RsPossibleMacroCall, PsiElement?>? {
        checkReadAccessAllowed()
        var parentVirtualFile = stubParent.virtualFile ?: return null
        if (parentVirtualFile !is VirtualFileWithId) return null
        while (true) {
            val info = storage.getInfoForExpandedFile(parentVirtualFile) ?: return null
            val macroCall = info.getMacroCall() ?: return null
            val macroCallContainingFile = info.sourceFile.file
            if (MacroExpansionManager.isExpansionFile(macroCallContainingFile)) {
                val parent = macroCall.stubParent
                if (parent is RsFile) {
                    check(parent.virtualFile == macroCallContainingFile)
                    parentVirtualFile = macroCallContainingFile
                    // continue
                } else {
                    return macroCall to parent
                }
            } else {
                return macroCall to macroCall.context
            }
        }
    }

    @TestOnly
    fun setupForUnitTests(saveCacheOnDispose: Boolean): Disposable {
        val disposable = Disposable { disposeUnitTest(saveCacheOnDispose) }

        setupListeners(disposable)

        ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
            private var isProcessingUpdates = false

            override fun afterWriteActionFinished(action: Any) {
                awaitAllTasksFinish()
            }

            private fun awaitAllTasksFinish() {
                check(isUnitTestMode)
                checkWriteAccessNotAllowed()
                val taskQueue = project.taskQueue
                if (!taskQueue.isEmpty) {
                    if (isProcessingUpdates) return
                    isProcessingUpdates = true
                    while (!taskQueue.isEmpty && !project.isDisposed) {
                        LaterInvocator.dispatchPendingFlushes()
                        Thread.sleep(10)
                    }
                    isProcessingUpdates = false
                }
            }
        }, disposable)

        // TODO this causes flaky tests. Expanding should be triggered by an actual code change
        processUnprocessedMacros()

        return disposable
    }

    private fun disposeUnitTest(saveCacheOnDispose: Boolean) {
        check(isUnitTestMode)

        project.taskQueue.cancelTasks(RsTask.TaskType.MACROS_CLEAR)

        val taskQueue = project.taskQueue
        if (!taskQueue.isEmpty) {
            while (!taskQueue.isEmpty && !project.isDisposed) {
                LaterInvocator.dispatchPendingFlushes()
                Thread.sleep(10)
            }
        }

        if (saveCacheOnDispose) {
            runBlocking {
                save()
            }
        } else {
            dirs.dataFile.delete()
        }

        pool.shutdownNow()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        releaseExpansionDirectory()
    }
}

/**
 * Ensures that [MacroExpansionManager] service is loaded when [CargoProjectsService] is initialized.
 * [MacroExpansionManager] should be loaded in order to add expansion directory to the index via
 * [RsIndexableSetContributor]
 */
class MacroExpansionManagerWaker : CargoProjectsListener {
    override fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>) {
        if (projects.isNotEmpty()) {
            service.project.macroExpansionManager
        }
    }
}

private fun expandMacroOld(call: RsMacroCall): CachedValueProvider.Result<MacroExpansion?> {
    // Most of std macros contain the only `impl`s which are not supported for now, so ignoring them
    if (call.containingCrate?.origin == PackageOrigin.STDLIB) {
        return nullExpansionResult(call)
    }
    return expandMacroToMemoryFile(
        call,
        // Old macros already consume too much memory, don't force them to consume more by range maps
        storeRangeMap = isUnitTestMode // false
    )
}

private fun expandMacroToMemoryFile(call: RsMacroCall, storeRangeMap: Boolean): CachedValueProvider.Result<MacroExpansion?> {
    val context = call.context as? RsElement ?: return nullExpansionResult(call)
    val def = call.resolveToMacroWithoutPsi() ?: return nullExpansionResult(call)
    val defData = def.data
    val project = call.project
    val result = FunctionLikeMacroExpander.new(project).expandMacro(
        defData,
        call,
        RsPsiFactory(project, markGenerated = false),
        storeRangeMap
    ).ok()
    result?.elements?.forEach {
        it.setContext(context)
        it.setExpandedFrom(call)
    }

    return CachedValueProvider.Result.create(
        result,
        call.rustStructureOrAnyPsiModificationTracker,
        call.modificationTracker
    )
}

private fun nullExpansionResult(call: RsMacroCall): CachedValueProvider.Result<MacroExpansion?> =
    CachedValueProvider.Result.create(null, call.rustStructureOrAnyPsiModificationTracker, call.modificationTracker)

private val RS_EXPANSION_MACRO_CALL = Key.create<RsMacroCall>("org.rust.lang.core.psi.RS_EXPANSION_MACRO_CALL")

@VisibleForTesting
fun RsExpandedElement.setExpandedFrom(call: RsMacroCall) {
    putUserData(RS_EXPANSION_MACRO_CALL, call)
}

private val VirtualFileSystem.isSupportedFs: Boolean
    get() = this is LocalFileSystem || this is MacroExpansionFileSystem

enum class MacroExpansionScope {
    ALL, WORKSPACE, NONE
}

sealed class MacroExpansionMode {
    object Disabled : MacroExpansionMode()
    object Old : MacroExpansionMode()
    data class New(val scope: MacroExpansionScope) : MacroExpansionMode()

    companion object {
        @JvmField
        val DISABLED: Disabled = Disabled

        @JvmField
        val OLD: Old = Old

        @JvmField
        val NEW_ALL: New = New(MacroExpansionScope.ALL)
    }
}

fun MacroExpansionMode.toScope(): MacroExpansionScope = when (this) {
    is MacroExpansionMode.New -> scope
    else -> MacroExpansionScope.NONE
}

private fun RustProjectSettingsService.MacroExpansionEngine.toMode(): MacroExpansionMode = when (this) {
    RustProjectSettingsService.MacroExpansionEngine.DISABLED -> MacroExpansionMode.DISABLED
    RustProjectSettingsService.MacroExpansionEngine.OLD -> MacroExpansionMode.OLD
    RustProjectSettingsService.MacroExpansionEngine.NEW -> MacroExpansionMode.NEW_ALL
}

val Project.macroExpansionManager: MacroExpansionManager get() = service()

// BACKCOMPAT 2019.3: use serviceIfCreated
val Project.macroExpansionManagerIfCreated: MacroExpansionManager?
    get() = this.getServiceIfCreated(MacroExpansionManager::class.java)

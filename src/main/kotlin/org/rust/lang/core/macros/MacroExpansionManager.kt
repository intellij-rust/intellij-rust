/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
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
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.RsFileType
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.crate.impl.FakeCrate
import org.rust.lang.core.indexing.RsIndexableSetContributor
import org.rust.lang.core.macros.errors.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsProcMacroKind.DERIVE
import org.rust.lang.core.psi.RsProcMacroKind.FUNCTION_LIKE
import org.rust.lang.core.psi.RsPsiTreeChangeEvent.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.*
import org.rust.openapiext.*
import org.rust.stdext.*
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import org.rust.taskQueue
import java.io.IOException
import java.lang.ref.SoftReference
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.Pair
import kotlin.io.path.exists

typealias MacroExpansionCachedResult = CachedValueProvider.Result<RsResult<MacroExpansion, GetMacroExpansionError>>

interface MacroExpansionManager {
    val indexableDirectory: VirtualFile?
    fun getExpansionFor(call: RsPossibleMacroCall): MacroExpansionCachedResult
    fun getExpandedFrom(element: RsExpandedElement): RsPossibleMacroCall?
    fun getIncludedFrom(file: RsFile): RsMacroCall?

    /**
     * An optimized equivalent for:
     * ```
     * when (val expandedFrom = getExpandedFrom(macroCall)?.kind) {
     *     is MacroCall -> expandedFrom.call.context
     *     is MetaItem -> expandedFrom.meta.owner?.context
     *     null -> null
     * }
     * ```
     * or `getExpandedFrom(macroCall)?.contextToSetForExpansion`
     */
    fun getContextOfMacroCallExpandedFrom(stubParent: RsFile): PsiElement?
    fun isExpansionFileOfCurrentProject(file: VirtualFile): Boolean
    fun getCrateForExpansionFile(file: VirtualFile): CratePersistentId?
    fun reexpand()

    val macroExpansionMode: MacroExpansionMode

    @TestOnly
    fun setUnitTestExpansionModeAndDirectory(
        mode: MacroExpansionScope,
        cacheDirectory: String = "",
        clearCacheBeforeDispose: Boolean = false
    ): Disposable
    @TestOnly
    fun updateInUnitTestMode()

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

    object Testmarks {
        object TooDeepExpansion : Testmark()
    }
}

@JvmField
val MACRO_LOG: Logger = Logger.getInstance("#org.rust.macros")

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

        ApplicationManager.getApplication().executeOnPooledThread {
            val impl = MacroExpansionServiceBuilder.build(project, dirs)
            MACRO_LOG.debug("Loading MacroExpansionManager - data loaded")

            invokeLater {
                runWriteAction {
                    if (isDisposed) return@runWriteAction
                    // Publish `inner` in the same write action where `stateLoaded` is called.
                    // The write action also makes it synchronized with `CargoProjectsService.initialized`
                    inner = impl
                    impl.stateLoaded(this)
                }
            }
        }
    }

    override fun noStateLoaded() {
        loadState(PersistentState(null))
    }

    override val indexableDirectory: VirtualFile?
        get() = inner?.expansionsDirVi

    override fun getExpansionFor(call: RsPossibleMacroCall): MacroExpansionCachedResult {
        val impl = inner
        return when {
            call is RsMacroCall && call.macroName == "include" -> expandIncludeMacroCall(call)
            impl != null -> impl.getExpansionFor(call)
            isUnitTestMode && call is RsMacroCall -> expandMacroOld(call)
            else -> CachedValueProvider.Result.create(
                Err(GetMacroExpansionError.MacroExpansionEngineIsNotReady),
                project.rustStructureModificationTracker
            )
        }
    }

    private fun expandIncludeMacroCall(call: RsMacroCall): MacroExpansionCachedResult {
        val expansion: RsResult<MacroExpansion, GetMacroExpansionError> = run {
            val includingFile = call.findIncludingFile()
                ?: return@run Err(GetMacroExpansionError.IncludingFileNotFound)
            if (getIncludedFrom(includingFile) != call) {
                return@run Err(GetMacroExpansionError.FileIncludedIntoMultiplePlaces)
            }
            val items = includingFile.stubChildrenOfType<RsExpandedElement>()
            Ok(MacroExpansion.Items(includingFile, items))
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

    override fun getIncludedFrom(file: RsFile): RsMacroCall? {
        return inner?.getIncludedFrom(file)
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

    override fun getCrateForExpansionFile(file: VirtualFile): CratePersistentId? =
        inner?.getCrateForExpansionFile(file)?.first

    override fun reexpand() {
        inner?.reexpand()
    }

    override val macroExpansionMode: MacroExpansionMode
        get() = inner?.expansionMode ?: MacroExpansionMode.OLD

    override fun setUnitTestExpansionModeAndDirectory(
        mode: MacroExpansionScope,
        cacheDirectory: String,
        clearCacheBeforeDispose: Boolean
    ): Disposable {
        check(isUnitTestMode)
        val dir = updateDirs(cacheDirectory.ifEmpty { null })
        val impl = MacroExpansionServiceBuilder.build(project, dir)
        this.dirs = dir
        this.inner = impl
        impl.macroExpansionMode = mode

        runWriteAction {
            ProjectRootManagerEx.getInstanceEx(project)
                .makeRootsChange(EmptyRunnable.getInstance(), false, true)
        }

        val saveCacheOnDispose = cacheDirectory.isNotEmpty()
        val disposable = impl.setupForUnitTests(saveCacheOnDispose, clearCacheBeforeDispose)

        Disposer.register(disposable) {
            this.inner = null
            this.dirs = null
        }
        return disposable
    }

    override fun updateInUnitTestMode() {
        inner?.updateInUnitTestMode()
    }

    override fun dispose() {
        inner?.dispose()
        isDisposed = true
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

private object MacroExpansionServiceBuilder {
    fun build(project: Project, dirs: Dirs): MacroExpansionServiceImplInner {
        val dataFile = dirs.dataFile
        MacroExpansionManager.checkInvalidatedStorage()
        MacroExpansionFileSystemRootsLoader.loadProjectDirs()
        val loadedFsDir = load(dataFile)

        val vfs = MacroExpansionFileSystem.getInstance()

        if (loadedFsDir != null) {
            vfs.setDirectory(dirs.expansionDirPath, loadedFsDir)
        } else {
            MACRO_LOG.debug("Using fresh ExpandedMacroStorage")
            vfs.createDirectoryIfNotExistsOrDummy(dirs.expansionDirPath)
        }

        val expansionsDirVi = vfs.refreshAndFindFileByPath(dirs.expansionDirPath)
            ?: error("Impossible because the directory is just created; ${dirs.expansionDirPath}")

        return MacroExpansionServiceImplInner(project, dirs, expansionsDirVi)
    }

    private fun load(dataFile: Path): MacroExpansionFileSystem.FSItem.FSDir? {
        return try {
            dataFile.newInflaterDataInputStream().use { data ->
                MacroExpansionFileSystem.readFSItem(data, null) as? MacroExpansionFileSystem.FSItem.FSDir
            }
        } catch (e: java.nio.file.NoSuchFileException) {
            null
        } catch (e: Exception) {
            MACRO_LOG.warn(e)
            null
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
    val expansionsDirVi: VirtualFile
) {
    val modificationTracker: SimpleModificationTracker = SimpleModificationTracker()

    @Volatile
    private var lastSavedStorageModCount: Long = modificationTracker.modificationCount

    private val lastUpdatedMacrosAt: MutableMap<CratePersistentId, Long> = hashMapOf()

    private val dataFile: Path
        get() = dirs.dataFile

    @TestOnly
    var macroExpansionMode: MacroExpansionScope = MacroExpansionScope.NONE

    fun isExpansionFileOfCurrentProject(file: VirtualFile): Boolean {
        return VfsUtil.isAncestor(expansionsDirVi, file, true)
    }

    fun getCrateForExpansionFile(virtualFile: VirtualFile): Pair<Int, String>? {
        if (!isExpansionFileOfCurrentProject(virtualFile)) return null
        val expansionName = virtualFile.name
        val crateId = virtualFile.parent.parent.parent.name.toIntOrNull() ?: return null
        return crateId to expansionName
    }

    suspend fun save() {
        if (lastSavedStorageModCount == modificationTracker.modificationCount) return

        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) { // ensure dispatcher knows we are doing blocking IO
            // Using a buffer to avoid IO in the read action
            // BACKCOMPAT: 2020.1 use async read action and extract `runReadAction` from `withContext`
            val (buffer, modCount) = runReadAction {
                val buffer = BufferExposingByteArrayOutputStream(1024 * 1024) // average stdlib storage size
                DataOutputStream(buffer).use { data ->
                    val dirToSave = MacroExpansionFileSystem.getInstance().getDirectory(dirs.expansionDirPath) ?: run {
                        MACRO_LOG.warn("Expansion directory does not exist when saving the component: ${dirs.expansionDirPath}")
                        MacroExpansionFileSystem.FSItem.FSDir(null, dirs.projectDirName)
                    }
                    MacroExpansionFileSystem.writeFSItem(data, dirToSave)
                }
                buffer to modificationTracker.modificationCount
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
        RefreshQueue.getInstance().refresh(/* async = */ !isUnitTestMode, /* recursive = */ true, {
            vfs.makeDummy(dirs.expansionDirPath)
        }, listOf(expansionsDirVi))
    }

    private fun cleanMacrosDirectoryAndStorage() {
        submitTask(object : Task.Backgroundable(project, "Cleaning outdated macros", false), RsTask {
            override fun run(indicator: ProgressIndicator) {
                if (!isUnitTestMode) checkReadAccessNotAllowed()
                val vfs = MacroExpansionFileSystem.getInstance()
                vfs.cleanDirectoryIfExists(dirs.expansionDirPath)
                vfs.createDirectoryIfNotExistsOrDummy(dirs.expansionDirPath)
                dirs.dataFile.delete()
                WriteAction.runAndWait<Throwable> {
                    VfsUtil.markDirtyAndRefresh(false, true, true, expansionsDirVi)
                    modificationTracker.incModificationCount()
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

    private fun submitTask(task: Task.Backgroundable) {
        project.taskQueue.run(task)
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
                // which value is changed after `inner` assigning
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
        connect.subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, treeChangeListener)
        project.rustPsiManager.subscribeRustPsiChange(connect, treeChangeListener)

        connect.subscribe(RustProjectSettingsService.RUST_SETTINGS_TOPIC, object : RustProjectSettingsService.RustSettingsListener {
            override fun rustSettingsChanged(e: RustProjectSettingsService.RustSettingsChangedEvent) {
                if (!e.affectsCargoMetadata) { // if affect cargo metadata, will be invoked by CARGO_PROJECTS_TOPIC
                    if (e.isChanged(RustProjectSettingsService.State::macroExpansionEngine)) {
                        settingsChanged()
                    }
                }
            }
        })
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
        if (!isExpansionModeNew) {
            cleanMacrosDirectoryAndStorage()
        }
        WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
            project.defMapService.scheduleRebuildAllDefMaps()
            project.rustPsiManager.incRustStructureModificationCount()
        }
        processUnprocessedMacros()
    }

    private enum class ChangedMacrosScope { NONE, CHANGED, UNPROCESSED }

    private inner class ChangedMacroUpdater : RsPsiTreeChangeAdapter(),
                                              RustPsiChangeListener,
                                              ApplicationListener,
                                              CargoProjectsListener {

        private var shouldProcessChangedMacrosOnWriteActionFinish: ChangedMacrosScope = ChangedMacrosScope.NONE

        override fun handleEvent(event: RsPsiTreeChangeEvent) {
            if (!isExpansionModeNew) return
            val file = event.file as? RsFile ?: return
            if (RsPsiManager.isIgnorePsiEvents(file)) return
            val virtualFile = file.virtualFile ?: return
            if (virtualFile !is VirtualFileWithId) return

            if (file.treeElement == null) return

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
                if (!MacroExpansionManager.isExpansionFile(virtualFile)) {
                    scheduleChangedMacrosUpdate(ChangedMacrosScope.CHANGED)
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
                scheduleChangedMacrosUpdate(ChangedMacrosScope.CHANGED)
                project.defMapService.onFileChanged(file)
            }
        }

        override fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>) {
            scheduleChangedMacrosUpdate(ChangedMacrosScope.UNPROCESSED)
        }

        override fun afterWriteActionFinished(action: Any) {
            val shouldProcessChangedMacros = shouldProcessChangedMacrosOnWriteActionFinish
            shouldProcessChangedMacrosOnWriteActionFinish = ChangedMacrosScope.NONE
            when (shouldProcessChangedMacros) {
                ChangedMacrosScope.NONE -> Unit
                ChangedMacrosScope.CHANGED -> processChangedMacros()
                ChangedMacrosScope.UNPROCESSED -> processUnprocessedMacros()
            }
        }

        private fun scheduleChangedMacrosUpdate(scope: ChangedMacrosScope) {
            shouldProcessChangedMacrosOnWriteActionFinish = scope
        }
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

    private fun processUnprocessedMacros() {
        MACRO_LOG.info("processUnprocessedMacros")
        processMacros(RsTask.TaskType.MACROS_UNPROCESSED)
    }

    private fun processChangedMacros() {
        MACRO_LOG.info("processChangedMacros")

        // Fixes inplace rename when the renamed element is referenced from a macro call body
        if (isTemplateActiveInAnyEditor()) return

        processMacros(RsTask.TaskType.MACROS_FULL)
    }

    private fun processMacros(taskType: RsTask.TaskType) {
        if (!isExpansionModeNew) return
        val task = MacroExpansionTask(
            project,
            modificationTracker,
            lastUpdatedMacrosAt,
            dirs.projectDirName,
            taskType,
        )
        submitTask(task)
    }

    private fun isTemplateActiveInAnyEditor(): Boolean {
        val tm = TemplateManager.getInstance(project)
        for (editor in FileEditorManager.getInstance(project).allEditors) {
            if (editor is TextEditor && tm.getActiveTemplate(editor.editor) != null) return true
        }

        return false
    }

    private fun <T> everChanged(result: T): CachedValueProvider.Result<T> =
        CachedValueProvider.Result.create(result, ModificationTracker.EVER_CHANGED)

    fun getExpansionFor(call: RsPossibleMacroCall): MacroExpansionCachedResult {
        if (expansionMode == MacroExpansionMode.DISABLED) {
            return everChanged(Err(GetMacroExpansionError.MacroExpansionIsDisabled))
        }

        if (expansionMode == MacroExpansionMode.OLD) {
            if (call !is RsMacroCall) return everChanged(Err(GetMacroExpansionError.MemExpAttrMacro))
            return expandMacroOld(call)
        }

        val containingFile = call.containingFile
        val containingRsFile = containingFile.containingRsFileSkippingCodeFragments
        val containingVirtualFile: VirtualFile? = containingFile.virtualFile
        val info = getModInfo(call.containingMod)
            ?: return everChanged(Err(GetMacroExpansionError.ModDataNotFound))

        if (containingRsFile != null && containingRsFile.macroExpansionDepth >= info.defMap.recursionLimit) {
            MacroExpansionManager.Testmarks.TooDeepExpansion.hit()
            return everChanged(Err(GetMacroExpansionError.TooDeepExpansion))
        }

        if (!call.isTopLevelExpansion || containingVirtualFile?.fileSystem?.isSupportedFs != true) {
            return expandMacroToMemoryFile(call, storeRangeMap = true)
        }

        val macroIndex = info.getMacroIndex(call, info.crate)
            ?: return everChanged(Err(getReasonWhyExpansionFileNotFound(call, info.crate, info.defMap, null)))
        val expansionFile = getExpansionFile(info.defMap, macroIndex)
            ?: return everChanged(Err(getReasonWhyExpansionFileNotFound(call, info.crate, info.defMap, macroIndex)))

        if (getExpandedFromByExpansionFile(expansionFile) != call) {
            return everChanged(Err(GetMacroExpansionError.InconsistentExpansionExpandedFrom))
        }

        val expansion = Ok(getExpansionFromExpandedFile(MacroExpansionContext.ITEM, expansionFile)!!)
        return if (call is RsMacroCall) {
            CachedValueProvider.Result.create(expansion, modificationTracker, call.modificationTracker)
        } else {
            CachedValueProvider.Result.create(
                expansion,
                modificationTracker,
                call.rustStructureOrAnyPsiModificationTracker
            )
        }
    }

    fun getExpandedFrom(element: RsExpandedElement): RsPossibleMacroCall? {
        checkReadAccessAllowed()
        val parent = element.stubParent as? RsFile ?: return null
        return getExpandedFromByExpansionFile(parent)
    }

    private fun getExpandedFromByExpansionFile(parent: RsFile) =
        CachedValuesManager.getCachedValue(parent, GET_EXPANDED_FROM_KEY) {
            CachedValueProvider.Result.create(
                doGetExpandedFromForExpansionFile(parent),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }

    private fun doGetExpandedFromForExpansionFile(parent: RsFile): RsPossibleMacroCall? {
        val (defMap, expansionName) = getDefMapForExpansionFile(parent) ?: return null
        val (modData, macroIndex, kind) = defMap.expansionNameToMacroCall[expansionName] ?: return null
        val crate = project.crateGraph.findCrateById(defMap.crate) ?: return null  // todo remove crate from RsModInfo
        val info = RsModInfo(project, defMap, modData, crate, dataPsiHelper = null)
        return info.findMacroCallByMacroIndex(macroIndex, kind)
    }

    fun getIncludedFrom(file: RsFile): RsMacroCall? {
        checkReadAccessAllowed()
        return CachedValuesManager.getCachedValue(file, GET_INCLUDED_FROM_KEY) {
            CachedValueProvider.Result.create(
                doGetIncludedFrom(file),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }

    private fun doGetIncludedFrom(file: RsFile): RsMacroCall? {
        val crate = file.crate
        val crateId = crate.id ?: return null
        val (defMap, modData, includeMacroIndex) = findFileInclusionPointsFor(file).find { it.defMap.crate == crateId }
            ?: return null
        if (includeMacroIndex == null) return null
        val info = RsModInfo(project, defMap, modData, crate, dataPsiHelper = null)
        return info.findMacroCallByMacroIndex(includeMacroIndex, FUNCTION_LIKE) as? RsMacroCall
    }

    /** @see MacroExpansionManager.getContextOfMacroCallExpandedFrom */
    fun getContextOfMacroCallExpandedFrom(stubParent: RsFile): PsiElement? {
        checkReadAccessAllowed()
        return CachedValuesManager.getCachedValue(stubParent, GET_CONTEXT_OF_MACRO_CALL_EXPANDED_FROM_KEY) {
            CachedValueProvider.Result.create(
                doGetContextOfMacroCallExpandedFrom(stubParent),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }

    fun doGetContextOfMacroCallExpandedFrom(stubParent: RsFile): PsiElement? {
        checkReadAccessAllowed()
        val (defMap, expansionName) = getDefMapForExpansionFile(stubParent) ?: return null
        val (modData, _, _) = defMap.expansionNameToMacroCall[expansionName] ?: return null
        return modData.toRsMod(project).singleOrNull()
    }

    private fun RsModInfo.findMacroCallByMacroIndex(macroIndex: MacroIndex, kind: RsProcMacroKind): RsPossibleMacroCall? {
        val ownerIndex = if (kind == DERIVE) macroIndex.parent else macroIndex

        val scope = findScope(ownerIndex) ?: return null
        val owner = scope.findItemWithMacroIndex(ownerIndex.last)
        return if (kind == FUNCTION_LIKE) {
            owner as? RsMacroCall
        } else {
            if (owner !is RsAttrProcMacroOwner) return null
            owner.findAttrOrDeriveMacroCall(macroIndex.last, kind == DERIVE, crate)
        }
    }

    private fun RsModInfo.findScope(ownerIndex: MacroIndex): RsMod? {
        val nestedIndices = mutableListOf<Int>()
        val nearestKnownParent: RsMod = run {
            val modIndex = modData.macroIndex
            var parentIndex = ownerIndex
            while (true) {
                parentIndex = parentIndex.parent
                if (MacroIndex.equals(parentIndex, modIndex)) {
                    return@run modData.toRsMod(this).singleOrNull()
                }
                if (parentIndex in defMap.macroCallToExpansionName) {
                    return@run getExpansionFile(defMap, parentIndex)
                }
                nestedIndices += parentIndex.last
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } ?: return null

        var parent: RsMod = nearestKnownParent
        for (macroIndexInParent in nestedIndices.asReversed()) {
            val macroCall = parent.findItemWithMacroIndex(macroIndexInParent) as? RsMacroCall
                ?: return null
            parent = macroCall.findIncludingFile() ?: return null
        }
        return parent
    }

    private fun RsAttrProcMacroOwner.findAttrOrDeriveMacroCall(
        macroIndexInParent: Int,
        isDerive: Boolean,
        crate: Crate,
    ): RsPossibleMacroCall? {
        val attrs = ProcMacroAttribute.getProcMacroAttributeWithoutResolve(
            this,
            explicitCrate = crate,
            withDerives = true
        )
        for (attr in attrs) {
            when (attr) {
                is ProcMacroAttribute.Attr -> if (!isDerive) {
                    return attr.attr
                }
                is ProcMacroAttribute.Derive -> if (isDerive) {
                    return attr.derives.elementAtOrNull(macroIndexInParent)
                }
            }
        }
        return null
    }

    private fun getExpansionFile(defMap: CrateDefMap, callIndex: MacroIndex): RsFile? {
        val expansionName = defMap.macroCallToExpansionName[callIndex] ?: return null
        // "/rust_expanded_macros/<projectId>/<crateId>/<mixHash>_<order>.rs"
        val expansionPath = "${defMap.crate}/${expansionNameToPath(expansionName)}"
        val file = expansionsDirVi.findFileByRelativePath(expansionPath) ?: return null
        if (!file.isValid) return null
        testAssert { file.fileType == RsFileType }
        return file.toPsiFile(project) as? RsFile
    }

    private fun getReasonWhyExpansionFileNotFound(
        call: RsPossibleMacroCall,
        crate: Crate,
        defMap: CrateDefMap,
        callIndex: MacroIndex?
    ): GetMacroExpansionError {
        if (!call.existsAfterExpansion(crate)) {
            return GetMacroExpansionError.CfgDisabled
        }

        val resolveResult = call.resolveToMacroWithoutPsiWithErr()

        val isProcMacro = resolveResult is Ok && resolveResult.ok.data is RsProcMacroData
            || resolveResult is Err && resolveResult.err is ResolveMacroWithoutPsiError.NoProcMacroArtifact

        val procMacroExperimentalFeature = when (val callKind = call.kind) {
            is RsPossibleMacroCallKind.MacroCall -> RsExperiments.FN_LIKE_PROC_MACROS
            is RsPossibleMacroCallKind.MetaItem -> if (RsProcMacroPsiUtil.canBeCustomDerive(callKind.meta)) {
                RsExperiments.DERIVE_PROC_MACROS
            } else {
                RsExperiments.ATTR_PROC_MACROS
            }
        }

        val procMacroExpansionIsDisabled = isProcMacro
            && (!isFeatureEnabled(EVALUATE_BUILD_SCRIPTS) || !isFeatureEnabled(PROC_MACROS) &&
                !isFeatureEnabled(procMacroExperimentalFeature))
        if (procMacroExpansionIsDisabled) {
            return GetMacroExpansionError.ExpansionError(ProcMacroExpansionError.ProcMacroExpansionIsDisabled)
        }

        resolveResult.unwrapOrElse { return it.toExpansionError() }

        if (callIndex == null) {
            return GetMacroExpansionError.NoMacroIndex
        }
        val expansionName = defMap.macroCallToExpansionName[callIndex]
            ?: return GetMacroExpansionError.ExpansionNameNotFound
        val mixHash = extractMixHashFromExpansionName(expansionName)
        val expansion = MacroExpansionSharedCache.getInstance().getExpansionIfCached(mixHash)
        // generic error if we don't know exact error
            ?: return GetMacroExpansionError.ExpansionFileNotFound
        val error = expansion.err()
            ?: return GetMacroExpansionError.InconsistentExpansionCacheAndVfs
        return GetMacroExpansionError.ExpansionError(error)
    }

    private fun getDefMapForExpansionFile(file: RsFile): Pair<CrateDefMap, String>? {
        val virtualFile = file.virtualFile ?: return null

        val (crateId, expansionName) = getCrateForExpansionFile(virtualFile) ?: return null

        val defMap = project.defMapService.getOrUpdateIfNeeded(crateId) ?: return null
        return defMap to expansionName
    }

    @TestOnly
    fun setupForUnitTests(saveCacheOnDispose: Boolean, clearCacheBeforeDispose: Boolean): Disposable {
        val disposable = Disposable { disposeUnitTest(saveCacheOnDispose, clearCacheBeforeDispose) }

        setupListeners(disposable)

        return disposable
    }

    @TestOnly
    fun updateInUnitTestMode() {
        processChangedMacros()
    }

    private fun disposeUnitTest(saveCacheOnDispose: Boolean, clearCacheBeforeDispose: Boolean) {
        check(isUnitTestMode)

        project.taskQueue.cancelTasks(RsTask.TaskType.MACROS_CLEAR)

        val taskQueue = project.taskQueue
        if (!taskQueue.isEmpty) {
            while (!taskQueue.isEmpty && !project.isDisposed) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(10)
            }
        }

        if (clearCacheBeforeDispose) {
            MacroExpansionFileSystem.getInstance().cleanDirectoryIfExists(dirs.expansionDirPath)
        }

        if (saveCacheOnDispose) {
            runBlocking {
                save()
            }
        } else {
            dirs.dataFile.delete()
        }

        releaseExpansionDirectory()
    }
}

private val GET_EXPANDED_FROM_KEY: Key<CachedValue<RsPossibleMacroCall?>> = Key.create("GET_EXPANDED_FROM_KEY")
private val GET_INCLUDED_FROM_KEY: Key<CachedValue<RsMacroCall?>> = Key.create("GET_INCLUDED_FROM_KEY")
private val GET_CONTEXT_OF_MACRO_CALL_EXPANDED_FROM_KEY: Key<CachedValue<PsiElement?>> =
    Key.create("GET_CONTEXT_OF_MACRO_CALL_EXPANDED_FROM_KEY")

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

private fun expandMacroOld(call: RsMacroCall): MacroExpansionCachedResult {
    // Most of std macros contain the only `impl`s which are not supported for now, so ignoring them
    if (call.containingCrate.origin == PackageOrigin.STDLIB) {
        return memExpansionResult(call, Err(GetMacroExpansionError.OldEngineStd))
    }
    return expandMacroToMemoryFile(
        call,
        // Old macros already consume too much memory, don't force them to consume more by range maps
        storeRangeMap = isUnitTestMode // false
    )
}

private fun expandMacroToMemoryFile(call: RsPossibleMacroCall, storeRangeMap: Boolean): MacroExpansionCachedResult {
    val modificationTrackers = getModificationTrackersForMemExpansion(call)

    val oldCachedResultSoftReference = call.getUserData(RS_EXPANSION_RESULT_SOFT)
    extractExpansionResult(oldCachedResultSoftReference, modificationTrackers)?.let { return it }

    val def = call.resolveToMacroWithoutPsiWithErr()
        .unwrapOrElse { return CachedValueProvider.Result(Err(it.toExpansionError()), modificationTrackers) }
    val crate = call.containingCrate
    if (crate is FakeCrate) return CachedValueProvider.Result(Err(GetMacroExpansionError.Unresolved), modificationTrackers)
    if (!call.isEnabledByCfg(crate)) {
        return CachedValueProvider.Result(Err(GetMacroExpansionError.CfgDisabled), modificationTrackers)
    }
    val result = FunctionLikeMacroExpander.forCrate(crate).expandMacro(
        def,
        call,
        storeRangeMap,
        useCache = true
    ).map { expansion ->
        val context = call.contextToSetForExpansion as? RsElement
        expansion.elements.forEach {
            it.setExpandedFrom(call)
            if (context != null) {
                it.setExpandedElementContext(context)
            }
        }
        if (context != null) {
            expansion.file.setRsFileContext(context, isInMemoryMacroExpansion = true)
        }
        expansion
    }.mapErr {
        when (it) {
            MacroExpansionAndParsingError.MacroCallSyntaxError -> GetMacroExpansionError.MacroCallSyntax
            is MacroExpansionAndParsingError.ParsingError -> GetMacroExpansionError.MemExpParsingError(
                it.expansionText,
                it.context
            )
            is MacroExpansionAndParsingError.ExpansionError -> GetMacroExpansionError.ExpansionError(it.error)
        }
    }

    if (result is Ok) {
        val newCachedResult = CachedMemExpansionResult(result, modificationTrackers.modificationCount(), call)

        // We want to guarantee that only one expansion RsFile per macro call exists in the memory at a time.
        // That is, if someone holds a strong reference to expansion PSI, the SoftReference must not be cleared.
        // This is possible if the expansion RsFile holds a strong reference to the object under SoftReference.
        result.ok.file.putUserData(RS_EXPANSION_RESULT, newCachedResult)

        val newCachedResultSoftReference = SoftReference(newCachedResult)
        var prevReference = oldCachedResultSoftReference
        while (!call.replace(RS_EXPANSION_RESULT_SOFT, prevReference, newCachedResultSoftReference)) {
            prevReference = call.getUserData(RS_EXPANSION_RESULT_SOFT)
            extractExpansionResult(prevReference, modificationTrackers)?.let { return it }
        }
    }

    return CachedValueProvider.Result(result, modificationTrackers)
}

private val RS_EXPANSION_RESULT_SOFT: Key<SoftReference<CachedMemExpansionResult>> =
    Key("org.rust.lang.core.macros.RS_EXPANSION_RESULT_SOFT")
private val RS_EXPANSION_RESULT: Key<CachedMemExpansionResult> = Key("org.rust.lang.core.macros.RS_EXPANSION_RESULT")

private data class CachedMemExpansionResult(
    val result: RsResult<MacroExpansion, GetMacroExpansionError>,
    val modificationCount: Long,
    // Guarantees the macro call is retained in the memory if links to expansion PSI exist
    val call: RsPossibleMacroCall,
)

private fun extractExpansionResult(
    cachedResultSoftReference: SoftReference<CachedMemExpansionResult>?,
    modificationTrackers: Array<Any>,
): CachedValueProvider.Result<RsResult<MacroExpansion, GetMacroExpansionError>>? {
    val cachedResult = cachedResultSoftReference?.get()
    if (cachedResult != null && cachedResult.modificationCount == modificationTrackers.modificationCount()) {
        return CachedValueProvider.Result(cachedResult.result, modificationTrackers)
    }

    return null
}

private fun memExpansionResult(
    call: RsPossibleMacroCall,
    result: RsResult<MacroExpansion, GetMacroExpansionError>
): MacroExpansionCachedResult {
    // Note: the cached result must be invalidated when `RsFile.cachedData` is invalidated
    val modificationTrackers = getModificationTrackersForMemExpansion(call)
    return CachedValueProvider.Result.create(result, modificationTrackers)
}

// Note: the cached result must be invalidated when `RsFile.cachedData` is invalidated
private fun getModificationTrackersForMemExpansion(call: RsPossibleMacroCall): Array<Any> {
    val structureModTracker = call.rustStructureOrAnyPsiModificationTracker
    return when {
        // Non-physical PSI does not have event system, but we can track the file changes
        !call.isPhysical -> arrayOf(structureModTracker, call.containingFile)

        call is RsMacroCall -> arrayOf(structureModTracker, call.modificationTracker)
        else -> arrayOf(structureModTracker)
    }
}

private fun Array<Any>.modificationCount(): Long = sumOf {
    when (it) {
        is ModificationTracker -> it.modificationCount
        is PsiFile -> it.modificationStamp
        else -> error("Unknown dependency: ${it.javaClass}")
    }
}

private val RS_EXPANSION_MACRO_CALL = Key.create<RsPossibleMacroCall>("org.rust.lang.core.psi.RS_EXPANSION_MACRO_CALL")

@VisibleForTesting
fun RsExpandedElement.setExpandedFrom(call: RsPossibleMacroCall) {
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

private fun RustProjectSettingsService.MacroExpansionEngine.toMode(): MacroExpansionMode = when (this) {
    RustProjectSettingsService.MacroExpansionEngine.DISABLED -> MacroExpansionMode.DISABLED
    RustProjectSettingsService.MacroExpansionEngine.OLD,
    RustProjectSettingsService.MacroExpansionEngine.NEW -> MacroExpansionMode.NEW_ALL
}

val Project.macroExpansionManager: MacroExpansionManager get() = service()

// BACKCOMPAT 2019.3: use serviceIfCreated
val Project.macroExpansionManagerIfCreated: MacroExpansionManager?
    get() = this.getServiceIfCreated(MacroExpansionManager::class.java)

// "abcdef_i.rs" → "a/b/abcdef_i.rs"
fun expansionNameToPath(name: String): String = "${name[0]}/${name[1]}/$name"

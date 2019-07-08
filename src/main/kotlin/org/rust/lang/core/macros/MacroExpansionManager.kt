/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.*
import com.intellij.openapiext.Testmark
import com.intellij.openapiext.isHeadlessEnvironment
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.PairConsumer
import com.intellij.util.concurrency.QueueProcessor
import com.intellij.util.concurrency.QueueProcessor.ThreadToUse
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IndexableFileSet
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsPsiTreeChangeEvent.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.indexes.RsMacroCallIndex
import org.rust.openapiext.*
import org.rust.stdext.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.*
import java.util.function.BiConsumer

interface MacroExpansionManager {
    val indexableDirectory: VirtualFile?
    fun getExpansionFor(call: RsMacroCall): CachedValueProvider.Result<MacroExpansion?>
    fun getExpandedFrom(element: RsExpandedElement): RsMacroCall?
    fun isExpansionFile(file: VirtualFile): Boolean
    fun reexpand()

    val macroExpansionMode: MacroExpansionMode

    var expansionState: ExpansionState?

    @TestOnly
    fun setUnitTestExpansionModeAndDirectory(mode: MacroExpansionScope, cacheDirectory: String = ""): Disposable

    data class ExpansionState(
        val expandedSearchScope: GlobalSearchScope,
        val stepModificationTracker: ModificationTracker
    )
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

val MACRO_LOG = Logger.getInstance("org.rust.macros")
// The path is visible in indexation progress
const val MACRO_EXPANSION_VFS_ROOT = "rust_expanded_macros"

fun getBaseMacroDir(): Path =
    Paths.get(PathManager.getSystemPath()).resolve("intellij-rust").resolve("macros")

@State(name = "MacroExpansionManager", storages = [
    Storage(StoragePathMacros.WORKSPACE_FILE),
    Storage("misc.xml", deprecated = true)
])
class MacroExpansionManagerImpl(
    val project: Project
) : MacroExpansionManager,
    ProjectComponent,
    PersistentStateComponent<MacroExpansionManagerImpl.PersistentState>,
    Disposable {

    data class PersistentState(var directoryName: String? = null)

    private var dirs: Dirs? = null
    private var innerFuture: Future<MacroExpansionServiceImplInner?>? = null
    private val inner: MacroExpansionServiceImplInner? get() = innerFuture?.waitForWithCheckCanceled()

    override fun getState(): PersistentState =
        PersistentState(inner?.save())

    override fun loadState(state: PersistentState) {
        // initialized manually at setUnitTestExpansionModeAndDirectory
        if (isUnitTestMode) return
        dirs = updateDirs(state.directoryName)
    }

    override fun noStateLoaded() {
        loadState(PersistentState(null))
    }

    override fun projectOpened() {
        if (isUnitTestMode) return // initialized manually at setUnitTestExpansionModeAndDirectory

        innerFuture = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            val preparedImpl = MacroExpansionServiceBuilder.prepare(project, dirs!!)
            runReadAction {
                if (project.isDisposed) return@runReadAction null
                val impl = preparedImpl.buildInReadAction()
                impl.projectOpened()
                impl
            }
        })
    }

    override val indexableDirectory: VirtualFile?
        get() = if (innerFuture?.isDone == true) inner?.expansionsDirVi else null

    override fun getExpansionFor(call: RsMacroCall): CachedValueProvider.Result<MacroExpansion?> {
        val impl = inner
        return when {
            call.macroName == "include" -> expandIncludeMacroCall(call)
            impl != null -> impl.getExpansionFor(call)
            isUnitTestMode -> expandMacroOld(call)
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

    override fun getExpandedFrom(element: RsExpandedElement): RsMacroCall? {
        // For in-memory expansions
        element.getUserData(RS_EXPANSION_MACRO_CALL)?.let { return it as RsMacroCall }

        val inner = inner
        return if (inner != null && inner.isExpansionModeNew) {
            inner.getExpandedFrom(element)
        } else {
            null
        }
    }

    override fun isExpansionFile(file: VirtualFile): Boolean =
        inner?.isExpansionFile(file) == true

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
        val impl = MacroExpansionServiceBuilder.prepare(project, dir).buildInReadAction()
        this.dirs = dir
        this.innerFuture = CompletableFuture.completedFuture(impl)
        impl.macroExpansionMode = mode
        val saveCacheOnDispose = cacheDirectory.isNotEmpty()
        val disposable = impl.setupForUnitTests(saveCacheOnDispose)
        Disposer.register(disposable, Disposable {
            this.innerFuture = null
            this.dirs = null
        })
        return disposable
    }

    override fun dispose() {
        inner?.dispose()
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
    private val project: Project,
    private val dirs: Dirs,
    private val serStorage: SerializedExpandedMacroStorage?,
    private val expansionsDirVi: VirtualFile?
){
    fun buildInReadAction(): MacroExpansionServiceImplInner {
        val storage = serStorage?.deserializeInReadAction(project) ?: ExpandedMacroStorage(project)
        return MacroExpansionServiceImplInner(project, dirs, storage, expansionsDirVi)
    }

    companion object {
        fun prepare(project: Project, dirs: Dirs): MacroExpansionServiceBuilder {
            val dataFile = dirs.dataFile
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
            val dir = vfs.findFileByPath(dirs.expansionDirPath) // Nullable!
            return MacroExpansionServiceBuilder(project, dirs, serStorage, dir)
        }

        private fun load(dataFile: Path): Pair<SerializedExpandedMacroStorage, MacroExpansionFileSystem.FSItem.FSDir>? {
            return try {
                dataFile.newInflaterDataInputStream().use { data ->
                    val sems = SerializedExpandedMacroStorage.load(data) ?: return null
                    val fs = MacroExpansionFileSystem.readFSItem(data, null) as? MacroExpansionFileSystem.FSItem.FSDir ?: return null
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
        val rem = MacroExpansionFileSystem.getInstance().getDirectory("/$MACRO_EXPANSION_VFS_ROOT")
            as? MacroExpansionFileSystem.FSItem.FSDir ?: return
        val dataFile = getProjectListDataFile()
        Files.createDirectories(dataFile.parent)
        dataFile.newDeflaterDataOutputStream().use { data ->
            val children = rem.copyChildren()
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
    var expansionsDirVi: VirtualFile?
) {
    private val taskQueue = MacroExpansionTaskQueue(project)

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

    fun isExpansionFile(file: VirtualFile): Boolean {
        val expansionsDirVi = expansionsDirVi ?: return false
        return VfsUtil.isAncestor(expansionsDirVi, file, true)
    }

    fun save(): String {
        // TODO async
        Files.createDirectories(dataFile.parent)
        dataFile.newDeflaterDataOutputStream().use { data ->
            ExpandedMacroStorage.saveStorage(storage, data)
            MacroExpansionFileSystem.writeFSItem(data, MacroExpansionFileSystem.getInstance().getDirectory(dirs.expansionDirPath))
        }
        MacroExpansionFileSystemRootsLoader.saveProjectDirs()
        return dirs.projectDirName
    }

    fun dispose() {
        val path = dirs.expansionDirPath
        val expansionsDirVi = expansionsDirVi ?: return

        // See [MacroExpansionFileSystem] docs for explanation of what happens here
        MacroExpansionFileSystem.getInstance().refreshFiles(listOf(expansionsDirVi), true, true) {
            MacroExpansionFileSystem.getInstance().makeDummy(path)
        }
    }

    private fun cleanMacrosDirectory() {
        taskQueue.run(object : Task.Backgroundable(project, "Cleaning outdated macros", false) {
            override fun run(indicator: ProgressIndicator) {
                checkReadAccessNotAllowed()
                val vfs = MacroExpansionFileSystem.getInstance()
                if (vfs.exists(dirs.expansionDirPath)) {
                    vfs.cleanDirectory(dirs.expansionDirPath)
                }
                vfs.createDirectoryIfNotExistsOrDummy(dirs.expansionDirPath)
                expansionsDirVi = vfs.refreshAndFindFileByPath(dirs.expansionDirPath)
                    ?: error("expected to be non-null because we just created it!")
                dirs.dataFile.delete()
                WriteAction.runAndWait<Throwable> {
                    VfsUtil.markDirtyAndRefresh(false, true, true, expansionsDirVi)
                    storage.clear()
                    if (!project.isDisposed) {
                        project.rustPsiManager.incRustStructureModificationCount()
                    }
                }
            }
        })
    }

    private fun checkStorageConsistency() {
        taskQueue.run(object : Task.Backgroundable(project, "Cleaning outdated macros", false) {
            override fun run(indicator: ProgressIndicator) {
                checkReadAccessNotAllowed()

                refreshExpansionDirectory()
                findAndDeleteLeakedExpansionFiles()
                findAndRemoveInvalidExpandedMacroInfosFromStorage()
            }

            private fun refreshExpansionDirectory() {
                expansionsDirVi = MacroExpansionFileSystem.getInstance().refreshAndFindFileByPath(dirs.expansionDirPath)
                    ?: error("Impossible because the directory just created in " +
                        "`MacroExpansionServiceBuilder.prepare`; ${dirs.expansionDirPath}")
                VfsUtil.markDirtyAndRefresh(false, true, false, expansionsDirVi!!)
            }

            private fun findAndDeleteLeakedExpansionFiles() {
                val toDelete = mutableListOf<VirtualFile>()
                runReadAction {
                    VfsUtil.iterateChildrenRecursively(expansionsDirVi!!, null, ContentIterator {
                        if (!it.isDirectory && storage.getInfoForExpandedFile(it) == null) {
                            toDelete += it
                        }
                        true
                    })
                }
                if (toDelete.isNotEmpty()) {
                    val batch = EventBasedVfsBatch()
                    toDelete.forEach { batch.deleteFile(it.path) }
                    WriteAction.runAndWait<Throwable> {
                        batch.applyToVfs()
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

    fun projectOpened() {
        check(!isUnitTestMode) // initialized manually at setUnitTestExpansionModeAndDirectory
        setupListeners()

        val cargoProjects = project.cargoProjects
        if (cargoProjects.initialized && cargoProjects.hasAtLeastOneValidProject) {
            processUnprocessedMacros()
        }
    }

    private fun setupListeners(disposable: Disposable? = null) {
        if (storage.isEmpty) {
            cleanMacrosDirectory()
        } else {
            checkStorageConsistency()
        }

        run {
            // Previous plugin versions stored expansion to this directory
            // TODO remove it someday
            val oldDirPath = Paths.get(PathManager.getSystemPath()).resolve("rust_expanded_macros")
            if (oldDirPath.exists()) {
                oldDirPath.delete()
                val oldDirVFile = LocalFileSystem.getInstance().findFileByIoFile(oldDirPath.toFile())
                if (oldDirVFile != null) {
                    VfsUtil.markDirtyAndRefresh(true, true, true, oldDirVFile)
                }
            }
        }

        val treeChangeListener = ChangedMacroUpdater()
        if (disposable != null) {
            PsiManager.getInstance(project).addPsiTreeChangeListener(treeChangeListener, disposable)
        } else {
            PsiManager.getInstance(project).addPsiTreeChangeListener(treeChangeListener)
        }
        ApplicationManager.getApplication().addApplicationListener(treeChangeListener, disposable ?: project)

        if (disposable != null) {
            check(isUnitTestMode)
            ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
                override fun afterWriteActionFinished(action: Any) {
                    ensureUpToDate()
                }
            }, disposable)
        }

        val indexableSet = object : IndexableFileSet {
            override fun isInSet(file: VirtualFile): Boolean =
                isExpansionFile(file)

            override fun iterateIndexableFilesIn(file: VirtualFile, iterator: ContentIterator) {
                VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Any>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (!isInSet(file)) {
                            return false
                        }

                        if (!file.isDirectory) {
                            iterator.processFile(file)
                        }

                        return true
                    }
                })
            }
        }

        val index = FileBasedIndex.getInstance()
        index.registerIndexableSet(indexableSet, project)
        if (disposable != null) {
            Disposer.register(disposable, Disposable { index.removeIndexableSet(indexableSet) })
        }

        val connect = if (disposable != null) project.messageBus.connect(disposable) else project.messageBus.connect()

        connect.subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
            override fun cargoProjectsUpdated(projects: Collection<CargoProject>) {
                if (!isExpansionModeNew) {
                    cleanMacrosDirectory()
                }
                processUnprocessedMacros()
            }
        })

        connect.subscribe(RUST_PSI_CHANGE_TOPIC, treeChangeListener)
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

            val macroCalls = element.descendantsOfTypeOrSelf<RsMacroCall>()
            if (macroCalls.isNotEmpty()) {
                val sf = storage.getOrCreateSourceFile(virtualFile) ?: return
                sf.newMacroCallsAdded(macroCalls)
                if (!isExpansionFile(virtualFile)) {
                    scheduleChangedMacrosUpdate(file.isWorkspaceMember())
                }
            }
        }

        override fun rustPsiChanged(file: PsiFile, element: PsiElement, isStructureModification: Boolean) {
            if (!isExpansionModeNew) return
            val shouldScheduleUpdate =
                (isStructureModification || element.ancestorOrSelf<RsMacroCall>()?.isTopLevelExpansion == true) &&
                    file.virtualFile?.let { isExpansionFile(it) } == false
            if (shouldScheduleUpdate && file is RsFile) {
                val isWorkspace = file.isWorkspaceMember()
                scheduleChangedMacrosUpdate(isWorkspace)
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
        cleanMacrosDirectory()
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
        val expansionsDirVi = expansionsDirVi ?: return GlobalSearchScope.allScope(project)
        val expansionDirs = (0 until step).mapNotNull {
            expansionsDirVi.findChild(it.toString())
        }
        val expansionScope = GlobalSearchScopes.directoriesScope(project, true, *expansionDirs.toTypedArray())
        return GlobalSearchScope.allScope(project).uniteWith(expansionScope)
    }

    private fun processUnprocessedMacros() {
        MACRO_LOG.info("processUnprocessedMacros")
        if (!isExpansionModeNew) return
        class ProcessUnprocessedMacrosTask : MacroExpansionTaskBase(
            project,
            storage,
            pool,
            ::vfsBatchFactory,
            ::createExpandedSearchScope,
            stepModificationTracker
        ) {
            override fun getMacrosToExpand(): Sequence<List<Extractable>> {
                val mode = expansionMode

                val scope = when (mode.toScope()) {
                    MacroExpansionScope.ALL -> GlobalSearchScope.allScope(project)
                    MacroExpansionScope.WORKSPACE -> GlobalSearchScope.projectScope(project)
                    MacroExpansionScope.NONE -> return emptySequence() // GlobalSearchScope.EMPTY_SCOPE
                }

                val calls = runReadActionInSmartMode(project) {
                    val calls = RsMacroCallIndex.getMacroCalls(project, scope)
                    MACRO_LOG.info("Macros to expand: ${calls.size}")
                    calls.groupBy { it.containingFile.virtualFile }

                }
                return storage.makeExpansionTask(calls)
            }

            override fun canEat(other: MacroExpansionTaskBase): Boolean = true // eat everything

            override val isProgressBarDelayed: Boolean get() = false
        }
        taskQueue.run(ProcessUnprocessedMacrosTask())
    }

    private fun processChangedMacros(workspaceOnly: Boolean) {
        MACRO_LOG.info("processChangedMacros")
        if (!isExpansionModeNew) return

        // Fixes inplace rename when the renamed element is referenced from a macro call body
        if (isTemplateActiveInAnyEditor()) return

        class ProcessModifiedMacrosTask(private val workspaceOnly: Boolean) : MacroExpansionTaskBase(
            project,
            storage,
            pool,
            ::vfsBatchFactory,
            ::createExpandedSearchScope,
            stepModificationTracker
        ) {
            override fun getMacrosToExpand(): Sequence<List<Extractable>> {
                return runReadAction { storage.makeValidationTask(workspaceOnly) }
            }

            override fun canEat(other: MacroExpansionTaskBase): Boolean {
                return other is ProcessModifiedMacrosTask && (other.workspaceOnly || !workspaceOnly)
            }
        }

        val task = ProcessModifiedMacrosTask(workspaceOnly)

        taskQueue.run(task)
    }

    private fun isTemplateActiveInAnyEditor(): Boolean {
        val tm = TemplateManager.getInstance(project)
        for (editor in FileEditorManager.getInstance(project).allEditors) {
            if (editor is TextEditor && tm.getActiveTemplate(editor.editor) != null) return true
        }

        return false
    }

    private fun ensureUpToDate() {
        check(isUnitTestMode)
        taskQueue.ensureUpToDate()
    }

    fun getExpansionFor(call: RsMacroCall): CachedValueProvider.Result<MacroExpansion?> {
        checkReadAccessAllowed()
        if (expansionState != null) return CachedValueProvider.Result.create(null, ModificationTracker.EVER_CHANGED)

        if (expansionMode == MacroExpansionMode.OLD) {
            return expandMacroOld(call)
        }

        if (!call.isTopLevelExpansion || call.containingFile.virtualFile?.fileSystem !is LocalFileSystem) {
            return expandMacroToMemoryFile(call, storeRangeMap = true)
        }

        val expansion = storage.getInfoForCall(call)?.getExpansion()
        return CachedValueProvider.Result.create(expansion, storage.modificationTracker, call.modificationTracker)
    }

    fun getExpandedFrom(element: RsExpandedElement): RsMacroCall? {
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

    @TestOnly
    fun setupForUnitTests(saveCacheOnDispose: Boolean): Disposable {
        val disposable = Disposable { disposeUnitTest(saveCacheOnDispose) }

        setupListeners(disposable)

        return disposable
    }

    private fun disposeUnitTest(saveCacheOnDispose: Boolean) {
        check(isUnitTestMode)

        taskQueue.cancelAll()

        if (saveCacheOnDispose) {
            save()
        } else {
            dirs.dataFile.delete()
        }
        dispose()
    }
}

/** Inspired by [BackgroundTaskQueue] */
private class MacroExpansionTaskQueue(val project: Project) {
    private val processor = QueueProcessor<ContinuableRunnable>(
        QueueConsumer(),
        true,
        ThreadToUse.AWT,
        project.disposed
    )

    // Guarded by self object monitor (@Synchronized)
    private val cancelableTasks: MutableList<BackgroundableTaskData> = mutableListOf()

    @Synchronized
    fun run(task: MacroExpansionTaskBase) {
        cancelableTasks.removeIf {
            if (it.task is MacroExpansionTaskBase && task.canEat(it.task)) {
                it.cancel()
                true
            } else {
                false
            }
        }
        val data = BackgroundableTaskData(task, ::onFinish)
        cancelableTasks += data
        processor.add(data)
    }

    fun run(task: Task.Backgroundable) {
        processor.add(BackgroundableTaskData(task) {})
    }

    fun runSimple(runnable: () -> Unit) {
        processor.add(SimpleTaskData(runnable))
    }

    private var isProcessingUpdates = false

    fun ensureUpToDate() {
        check(isUnitTestMode)
        if (ApplicationManager.getApplication().isDispatchThread && !processor.isEmpty) {
            checkWriteAccessNotAllowed()
            if (isProcessingUpdates) return
            isProcessingUpdates = true
            while (!processor.isEmpty && !project.isDisposed) {
                LaterInvocator.dispatchPendingFlushes()
                Thread.sleep(10)
            }
            isProcessingUpdates = false
        }
    }

    @Synchronized
    fun cancelAll() {
        for (task in cancelableTasks) {
            task.cancel()
        }
        cancelableTasks.clear()
    }

    @Synchronized
    private fun onFinish(data: BackgroundableTaskData) {
        cancelableTasks.remove(data)
    }

    private interface ContinuableRunnable {
        fun run(continuation: Runnable)
    }

    // BACKCOMPAT: 2019.3. get rid of [PairConsumer] implementation
    private class QueueConsumer : PairConsumer<ContinuableRunnable, Runnable>, BiConsumer<ContinuableRunnable, Runnable> {
        override fun consume(s: ContinuableRunnable, t: Runnable) = accept(s, t)
        override fun accept(t: ContinuableRunnable, u: Runnable) = t.run(u)
    }

    private class BackgroundableTaskData(
        val task: Task.Backgroundable,
        val onFinish: (BackgroundableTaskData) -> Unit
    ) : ContinuableRunnable {
        private var state: State = State.Pending

        @Synchronized
        override fun run(continuation: Runnable) {
            // BackgroundableProcessIndicator should be created from EDT
            checkIsDispatchThread()
            if (state != State.Pending) {
                continuation.run()
                return
            }

            val indicator = when {
                isHeadlessEnvironment -> EmptyProgressIndicator()
                task is MacroExpansionTaskBase && task.isProgressBarDelayed -> DelayedBackgroundableProcessIndicator(task, 2000)
                else -> BackgroundableProcessIndicator(task)
            }

            state = State.Running(indicator)

            val pm = ProgressManager.getInstance() as ProgressManagerImpl
            pm.runProcessWithProgressAsynchronously(
                task,
                indicator,
                {
                    onFinish(this)
                    continuation.run()
                },
                ModalityState.NON_MODAL
            )
        }

        @Synchronized
        fun cancel() {
            when (val state = state) {
                State.Pending -> this.state = State.Canceled
                is State.Running -> state.indicator.cancel()
                State.Canceled -> Unit
            }
        }

        private sealed class State {
            object Pending : State()
            object Canceled : State()
            data class Running(val indicator: ProgressIndicator) : State()
        }
    }

    private class SimpleTaskData(val task: () -> Unit) : ContinuableRunnable {
        override fun run(continuation: Runnable) {
            try {
                task()
            } finally {
                continuation.run()
            }
        }

    }
}

private fun expandMacroOld(call: RsMacroCall): CachedValueProvider.Result<MacroExpansion?> {
    // Most of std macros contain the only `impl`s which are not supported for now, so ignoring them
    if (call.containingCargoTarget?.pkg?.origin == PackageOrigin.STDLIB) {
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
    val def = call.resolveToMacro() ?: return nullExpansionResult(call)
    val project = call.project
    val result = MacroExpander(project).expandMacro(
        def,
        call,
        RsPsiFactory(project, markGenerated = false),
        storeRangeMap
    )
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

private val RS_EXPANSION_MACRO_CALL = Key.create<RsElement>("org.rust.lang.core.psi.RS_EXPANSION_MACRO_CALL")

private fun RsExpandedElement.setExpandedFrom(call: RsMacroCall) {
    putUserData(RS_EXPANSION_MACRO_CALL, call)
}

enum class MacroExpansionScope {
    ALL, WORKSPACE, NONE
}

sealed class MacroExpansionMode {
    object Disabled : MacroExpansionMode()
    object Old : MacroExpansionMode()
    data class New(val scope: MacroExpansionScope) : MacroExpansionMode()

    companion object {
        val DISABLED = MacroExpansionMode.Disabled
        val OLD = MacroExpansionMode.Old
        val NEW_ALL = New(MacroExpansionScope.ALL)
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

val Project.macroExpansionManager: MacroExpansionManager
    get() = getComponent(MacroExpansionManager::class.java)

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import org.rust.RsTask.TaskType.*
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsPsiTreeChangeEvent.*
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.pathAsPath
import org.rust.stdext.mapToSet
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/** Stores [CrateDefMap] and data needed to determine whether [defMap] is up-to-date. */
class DefMapHolder(private val structureModificationTracker: ModificationTracker) {

    /**
     * Write access requires read action with [DefMapService.defMapsBuildLock] or write action.
     * Read access requires only read action (needed for fast path in [DefMapService.getOrUpdateIfNeeded]).
     */
    @Volatile
    var defMap: CrateDefMap? = null
        private set

    /**
     * Value of [rustStructureModificationTracker] at the time when [defMap] started to built.
     * Write access requires read action with [DefMapService.defMapsBuildLock] or write action.
     * Read access requires only read action (needed for fast path in [DefMapService.getOrUpdateIfNeeded]).
     */
    private val defMapStamp: AtomicLong = AtomicLong(-1)

    fun hasLatestStamp(): Boolean = defMapStamp.get() == structureModificationTracker.modificationCount

    private fun setLatestStamp() {
        defMapStamp.set(structureModificationTracker.modificationCount)
    }

    fun checkHasLatestStamp() {
        if (!hasLatestStamp()) {
            RESOLVE_LOG.error(
                "DefMapHolder must have latest stamp right after DefMap($defMap) was updated. " +
                    "$defMapStamp vs ${structureModificationTracker.modificationCount}"
            )
        }
    }

    /**
     * If true then we should rebuild [defMap], regardless of [shouldRecheck] or [changedFiles] values.
     * Any access requires read action with [DefMapService.defMapsBuildLock] or write action.
     */
    @Volatile
    var shouldRebuild: Boolean = true
        set(value) {
            field = value
            if (value) {
                defMapStamp.decrementAndGet()
                shouldRecheck = false
                changedFiles.clear()
            }
        }

    /**
     * If true then we should check for possible modification every file of the crate
     * (using [FileInfo.modificationStamp] or [HashCalculator]).
     * Any access requires read action with [DefMapService.defMapsBuildLock] or write action.
     */
    @Volatile
    var shouldRecheck: Boolean = false
        set(value) {
            field = value
            if (value) {
                defMapStamp.decrementAndGet()
            }
        }

    /** Any access requires read action with [DefMapService.defMapsBuildLock] or write action. */
    val changedFiles: MutableSet<RsFile> = hashSetOf()
    fun addChangedFile(file: RsFile) {
        changedFiles += file
        defMapStamp.decrementAndGet()
    }

    fun setDefMap(defMap: CrateDefMap?) {
        this.defMap = defMap
        shouldRebuild = false
        setLatestStamp()
    }

    fun updateShouldRebuild(crate: Crate): Boolean {
        val shouldRebuild = getShouldRebuild(crate)
        if (shouldRebuild) {
            this.shouldRebuild = true
        } else {
            setLatestStamp()
        }
        return shouldRebuild
    }

    override fun toString(): String = "DefMapHolder($defMap, stamp=$defMapStamp)"
}

@Service
class DefMapService(val project: Project) : Disposable {

    /** Concurrent because [DefMapsBuilder] uses multiple threads */
    private val defMaps: ConcurrentHashMap<CratePersistentId, DefMapHolder> = ConcurrentHashMap()
    val defMapsBuildLock: ReentrantLock = ReentrantLock()

    private val fileIdToCrateId: ConcurrentHashMap<FileId, CratePersistentId> = ConcurrentHashMap()

    /** Merged map of [CrateDefMap.missedFiles] for all crates */
    private val missedFiles: ConcurrentHashMap<Path, CratePersistentId> = ConcurrentHashMap()

    private val structureModificationTracker: ModificationTracker =
        project.rustPsiManager.rustStructureModificationTracker

    init {
        setupListeners()
    }

    /**
     * Possible modifications:
     * - After IDE restart: full recheck (for each crate compare [CrateMetaData] and `modificationStamp` of each file).
     *   Tasks [CARGO_SYNC] and [MACROS_UNPROCESSED] are executed.
     * - File changed: calculate hash and compare with hash stored in [CrateDefMap.fileInfos].
     *   Task [MACROS_WORKSPACE] is executed.
     * - File added: check whether [missedFiles] contains file path
     * - File deleted: check whether [fileIdToCrateId] contains this file
     * - Crate workspace changed: full recheck
     *   Tasks [CARGO_SYNC] and [MACROS_UNPROCESSED] are executed.
     */
    private fun setupListeners() {
        PsiManager.getInstance(project).addPsiTreeChangeListener(DefMapPsiTreeChangeListener(), this)

        val connection = project.messageBus.connect()

        project.rustPsiManager.subscribeRustPsiChange(connection, object : RustPsiChangeListener {
            override fun rustPsiChanged(file: PsiFile, element: PsiElement, isStructureModification: Boolean) {
                /** When macro expansion is enabled, file modification is handled in `ChangedMacroUpdater.rustPsiChanged` */
                if (file is RsFile && project.macroExpansionManager.macroExpansionMode !is MacroExpansionMode.New) {
                    onFileChanged(file)
                }
            }
        })

        connection.subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsListener {
            override fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>) {
                scheduleRecheckAllDefMaps()
            }
        })
    }

    fun getDefMapHolder(crate: CratePersistentId): DefMapHolder {
        return defMaps.computeIfAbsent(crate) { DefMapHolder(structureModificationTracker) }
    }

    fun setDefMap(crate: CratePersistentId, defMap: CrateDefMap?) {
        updateFilesMaps(crate, defMap)

        val holder = getDefMapHolder(crate)
        holder.setDefMap(defMap)
    }

    private fun updateFilesMaps(crate: CratePersistentId, defMap: CrateDefMap?) {
        fileIdToCrateId.values.remove(crate)
        missedFiles.values.remove(crate)
        if (defMap != null) {
            for (fileId in defMap.fileInfos.keys) {
                fileIdToCrateId[fileId] = crate
            }
            for (missedFile in defMap.missedFiles) {
                missedFiles[missedFile] = crate
            }
        }
    }

    private fun onFileAdded(file: RsFile) {
        checkWriteAccessAllowed()
        val path = file.virtualFile.pathAsPath
        val crate = missedFiles[path] ?: return
        getDefMapHolder(crate).shouldRebuild = true
    }

    private fun onFileRemoved(file: RsFile) {
        checkWriteAccessAllowed()
        val crate = findCrate(file) ?: return
        getDefMapHolder(crate).shouldRebuild = true
    }

    fun onFileChanged(file: RsFile) {
        if (!isNewResolveEnabled) return
        checkWriteAccessAllowed()
        val crate = findCrate(file) ?: return
        getDefMapHolder(crate).addChangedFile(file)
    }

    /** Note: we can't use [RsFile.crate], because it can trigger resolve */
    private fun findCrate(file: RsFile): CratePersistentId? {
        /** Virtual file can be [VirtualFileWindow] if it is doctest injection */
        val virtualFile = file.virtualFile as? VirtualFileWithId ?: return null
        return fileIdToCrateId[virtualFile.id]
    }

    private fun scheduleRecheckAllDefMaps() {
        checkWriteAccessAllowed()
        for (defMapHolder in defMaps.values) {
            defMapHolder.shouldRecheck = true
        }
    }

    /** Removes DefMaps for crates not in crate graph */
    fun removeStaleDefMaps(allCrates: List<Crate>) {
        val allCrateIds = allCrates.mapToSet { it.id }
        val staleCrates = mutableListOf<CratePersistentId>()
        defMaps.keys.removeIf { crate ->
            val isStale = crate !in allCrateIds
            if (isStale) staleCrates += crate
            isStale
        }
        for (staleCrate in staleCrates) {
            fileIdToCrateId.values.remove(staleCrate)
            missedFiles.values.remove(staleCrate)
        }
    }

    override fun dispose() {}

    private inner class DefMapPsiTreeChangeListener : RsPsiTreeChangeAdapter() {
        override fun handleEvent(event: RsPsiTreeChangeEvent) {
            if (!isNewResolveEnabled) return
            // events for file addition/deletion have null `event.file` and not-null `event.child`
            if (event.file != null) return
            when (event) {
                is ChildAddition.After -> {
                    val file = event.child as? RsFile ?: return
                    onFileAdded(file)
                }
                is ChildRemoval.Before -> {
                    val file = event.child as? RsFile ?: return
                    onFileRemoved(file)
                }
                is PropertyChange.Before -> {  // before rename
                    if (event.propertyName == PsiTreeChangeEvent.PROP_FILE_NAME) {
                        val file = event.child as? RsFile ?: return
                        onFileRemoved(file)
                    }
                }
                is PropertyChange.After -> {  // after rename
                    if (event.propertyName == PsiTreeChangeEvent.PROP_FILE_NAME) {
                        val file = event.element as? RsFile ?: return
                        onFileAdded(file)
                        return
                    }
                }
                else -> Unit
            }
        }
    }
}

val Project.defMapService: DefMapService
    get() = service()

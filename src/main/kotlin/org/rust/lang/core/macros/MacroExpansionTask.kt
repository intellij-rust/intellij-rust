/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.rust.RsTask
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem
import org.rust.lang.core.macros.MacroExpansionFileSystem.TrustedRequestor
import org.rust.lang.core.psi.RsPsiManager
import org.rust.lang.core.resolve2.CrateDefMap
import org.rust.lang.core.resolve2.DefCollector
import org.rust.lang.core.resolve2.MacroIndex
import org.rust.lang.core.resolve2.updateDefMapForAllCrates
import org.rust.openapiext.*
import org.rust.stdext.HashCode
import org.rust.stdext.mapToSet
import java.io.IOException

/**
 * Overview of macro expansion process:
 * Macros are expanded during [CrateDefMap] building and saved to [MacroExpansionSharedCache].
 * Among with that we statically generate names of expansion files (`<mixHash>_<order>.rs`).
 * [MacroExpansionTask] creates and deletes needed expansion files based on [CrateDefMap.expansionNameToMacroCall].
 * Expansions are taken from [MacroExpansionSharedCache].
 *
 * Linking with PSI is done using [MacroIndex].
 *
 *
 * Here is typical path of expansion file:
 * "/rust_expanded_macros/<random_project_id>/<crate_id>/a/b/<mix_hash>_<order>.rs"
 * List of methods that rely on such path format:
 * - [DefCollector.recordExpansionFileName]
 * - [expansionNameToPath]
 * - [VirtualFile.loadMixHash]
 * - [MacroExpansionServiceImplInner.getDefMapForExpansionFile]
 * - [MacroExpansionTask.collectExistingFiles]
 */
class MacroExpansionTask(
    project: Project,
    private val modificationTracker: SimpleModificationTracker,
    private val lastUpdatedMacrosAt: MutableMap<CratePersistentId, Long>,
    private val projectDirectoryName: String,
    override val taskType: RsTask.TaskType,
) : Task.Backgroundable(project, "Expanding Rust macros", /* canBeCancelled = */ false),
    RsTask {
    private val expansionFileSystem: MacroExpansionFileSystem = MacroExpansionFileSystem.getInstance()

    override fun run(indicator: ProgressIndicator) {
        indicator.checkCanceled()
        indicator.isIndeterminate = false
        val subTaskIndicator = indicator.toThreadSafeProgressIndicator()

        val start1 = System.currentTimeMillis()

        val allDefMaps = try {
            indicator.text = "Preparing resolve data"
            updateDefMapForAllCrates(project, subTaskIndicator)
        } catch (e: ProcessCanceledException) {
            throw e
        }

        val start2 = System.currentTimeMillis()
        val elapsed1 = start2 - start1
        MACRO_LOG.debug("Finished building DefMaps for all crates in $elapsed1 ms")

        indicator.text = "Save macro expansions"
        updateMacrosFiles(allDefMaps)

        val elapsed2 = System.currentTimeMillis() - start2
        MACRO_LOG.debug("Finished macro expansion task in $elapsed2 ms")
    }

    private class FileCreation(
        val crate: CratePersistentId,
        val expansionName: String,
        val content: String,
        val ranges: RangeMap,
    )

    private class FileDeletion(
        val crate: CratePersistentId,
        val expansionName: String,
        val file: FSItem.FSFile,
    )

    private data class FileAttributes(
        val path: MacroExpansionVfsBatch.Path,
        val rangeMap: RangeMap,
    )

    private fun updateMacrosFiles(allDefMaps: List<CrateDefMap>) {
        val contentRoot = "/$MACRO_EXPANSION_VFS_ROOT/$projectDirectoryName"
        val batch = MacroExpansionVfsBatch(contentRoot)
        val hasStaleExpansions = deleteStaleExpansions(allDefMaps, batch)

        val defMaps = allDefMaps.filter {
            lastUpdatedMacrosAt[it.crate] != it.timestamp
        }
        if (!hasStaleExpansions && defMaps.isEmpty()) return

        val files = collectFilesForCreationAndDeletion(defMaps)

        val singleChangeApplied = !batch.hasChanges
            && files.first.size == 1
            && files.second.size == 1
            && applySingleChange(contentRoot, files.first.single(), files.second.single())

        if (!singleChangeApplied) {
            val filesToWriteAttributes = createOrDeleteNeededFiles(files, batch)
            applyBatchAndWriteAttributes(batch, filesToWriteAttributes)
        }

        for (defMap in defMaps) {
            lastUpdatedMacrosAt[defMap.crate] = defMap.timestamp
        }
    }

    private fun collectFilesForCreationAndDeletion(defMaps: List<CrateDefMap>): Pair<List<FileCreation>, List<FileDeletion>> {
        val expansionSharedCache = MacroExpansionSharedCache.getInstance()
        val pendingFileWrites = mutableListOf<FileCreation>()
        val pendingFileDeletions = mutableListOf<FileDeletion>()
        for (defMap in defMaps) {
            val existingFiles = collectExistingFiles(defMap.crate)

            val requiredExpansions = defMap.expansionNameToMacroCall
            val filesToDelete = existingFiles.keys - requiredExpansions.keys
            val filesToCreate = requiredExpansions.keys - existingFiles.keys
            for (expansionName in filesToCreate) {
                /**
                 * Note that we don't use expansion text here, and ideally shouldn't load expansion at all.
                 * Expansion text is provided in [MacroExpansionFileSystem.contentsToByteArray].
                 */
                val mixHash = extractMixHashFromExpansionName(expansionName)
                val expansion = expansionSharedCache.getExpansionIfCached(mixHash)?.ok() ?: continue

                val ranges = expansion.ranges
                pendingFileWrites += FileCreation(defMap.crate, expansionName, expansion.text, ranges)
            }
            for (fileName in filesToDelete) {
                val file = existingFiles.getValue(fileName)
                pendingFileDeletions += FileDeletion(defMap.crate, fileName, file)
            }
        }
        return pendingFileWrites to pendingFileDeletions
    }

    /** An optimization for single macro change (i.e. typing in a macro call) */
    private fun applySingleChange(
        contentRoot: String,
        singleWrite: FileCreation,
        singleDeletion: FileDeletion
    ): Boolean {
        val oldPath = "$contentRoot/${singleDeletion.crate}/${expansionNameToPath(singleDeletion.expansionName)}"
        val newPath = "$contentRoot/${singleWrite.crate}/${expansionNameToPath(singleWrite.expansionName)}"
        val lastSlash = newPath.lastIndexOf('/')
        if (lastSlash == -1) error("unreachable")
        val newParentPath = newPath.substring(0, lastSlash)
        val newName = newPath.substring(lastSlash + 1)

        return invokeAndWaitIfNeeded {
            val root = MacroExpansionFileSystem.getInstance().findFileByPath("/")!!
            val oldFile = MacroExpansionFileSystem.getInstance().findFileByPath(oldPath)
                ?: return@invokeAndWaitIfNeeded false
            val oldPsiFile = oldFile.toPsiFile(project) ?: return@invokeAndWaitIfNeeded false
            val (nearestNewParentFile, segmentsToCreate) = root.findNearestExistingFile(newParentPath)

            runWriteAction {
                try {
                    var newFileParent = nearestNewParentFile
                    for (segment in segmentsToCreate) {
                        newFileParent = newFileParent.createChildDirectory(TrustedRequestor, segment)
                    }
                    RsPsiManager.withIgnoredPsiEvents(oldPsiFile) {
                        oldFile.move(TrustedRequestor, newFileParent)
                        oldFile.rename(TrustedRequestor, newName)
                    }
                    oldFile.getOutputStream(TrustedRequestor).use {
                        it.write(singleWrite.content.toByteArray())
                    }
                    oldFile.writeRangeMap(singleWrite.ranges)
                } catch (e: IOException) {
                    MACRO_LOG.error(e)
                    return@runWriteAction false
                }

                if (isUnitTestMode && runSyncInUnitTests) {
                    // In unit tests macro expansion task works synchronously, so we have to
                    // commit the document synchronously too
                    val doc = FileDocumentManager.getInstance().getCachedDocument(oldFile)
                    if (doc != null) {
                        PsiDocumentManager.getInstance(project).commitDocument(doc)
                    }
                }

                modificationTracker.incModificationCount()
                true
            }
        }
    }

    private fun createOrDeleteNeededFiles(
        files: Pair<List<FileCreation>, List<FileDeletion>>,
        batch: MacroExpansionVfsBatch
    ): List<FileAttributes> {
        val filesToWriteAttributes = mutableListOf<FileAttributes>()
        for (fileCreation in files.first) {
            val path = batch.createFile(fileCreation.crate, fileCreation.expansionName, fileCreation.content, implicit = true)
            filesToWriteAttributes += FileAttributes(path, fileCreation.ranges)
        }
        for (fileDeletion in files.second) {
            batch.deleteFile(fileDeletion.file)
        }
        return filesToWriteAttributes
    }

    private fun applyBatchAndWriteAttributes(batch: MacroExpansionVfsBatch, files: List<FileAttributes>) {
        if (!batch.hasChanges) return
        batch.applyToVfs(false) {
            for ((path, ranges) in files) {
                val virtualFile = path.toVirtualFile() ?: continue
                virtualFile.writeRangeMap(ranges)
            }
            modificationTracker.incModificationCount()
        }
    }

    private fun collectExistingFiles(crate: CratePersistentId): Map<String, FSItem.FSFile> {
        val path = "/$MACRO_EXPANSION_VFS_ROOT/$projectDirectoryName/$crate"
        val rootDirectory = expansionFileSystem.getDirectory(path) ?: return emptyMap()
        return rootDirectory
            .copyChildren().asSequence().filterIsInstance<FSItem.FSDir>()     //  first level directories
            .flatMap { it.copyChildren() }.filterIsInstance<FSItem.FSDir>()   // second level directories
            .flatMap { it.copyChildren() }.filterIsInstance<FSItem.FSFile>()  // actual files
            .associateBy { it.name }
    }

    private fun deleteStaleExpansions(allDefMaps: List<CrateDefMap>, batch: MacroExpansionVfsBatch): Boolean {
        val crates = allDefMaps.mapToSet { it.crate.toString() }

        lastUpdatedMacrosAt.entries.removeIf { (crate, _) ->
            crate.toString() !in crates
        }

        val path = "/$MACRO_EXPANSION_VFS_ROOT/$projectDirectoryName"
        val root = expansionFileSystem.getDirectory(path) ?: return false
        root.copyChildren()
            .filterIsInstance<FSItem.FSDir>()
            .filter { it.name !in crates }
            .ifEmpty { return false }
            .forEach { batch.deleteFile(it) }
        return true
    }

    override val waitForSmartMode: Boolean
        get() = true

    override val progressBarShowDelay: Int
        get() = when (taskType) {
            RsTask.TaskType.MACROS_UNPROCESSED -> 0
            else -> 2000
        }

    override val runSyncInUnitTests: Boolean
        get() = true
}

// "<mixHash>_<order>.rs" → "<mixHash>"
fun extractMixHashFromExpansionName(name: String): HashCode {
    testAssert { name.endsWith(".rs") && name.contains('_') }
    val index = name.indexOf('_')
    check(index != -1)
    val mixHash = name.substring(0, index)
    return HashCode.fromHexString(mixHash)
}

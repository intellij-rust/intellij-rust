/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import org.rust.RsTask
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem
import org.rust.lang.core.resolve2.CrateDefMap
import org.rust.lang.core.resolve2.DefCollector
import org.rust.lang.core.resolve2.MacroIndex
import org.rust.lang.core.resolve2.updateDefMapForAllCrates
import org.rust.openapiext.testAssert
import org.rust.openapiext.toThreadSafeProgressIndicator
import org.rust.stdext.HashCode
import org.rust.stdext.mapToSet
import java.util.concurrent.ExecutorService

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
    private val pool: ExecutorService,
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

        val allDefMaps = try {
            indicator.text = "Preparing resolve data"
            updateDefMapForAllCrates(project, pool, subTaskIndicator)
        } catch (e: ProcessCanceledException) {
            throw e
        }

        indicator.text = "Save macro expansions"
        updateMacrosFiles(allDefMaps)
    }

    private data class FileAttributes(
        val path: MacroExpansionVfsBatch.Path,
        val rangeMap: RangeMap,
    )

    private fun updateMacrosFiles(allDefMaps: List<CrateDefMap>) {
        val batch = MacroExpansionVfsBatch("/$MACRO_EXPANSION_VFS_ROOT/$projectDirectoryName")
        val hasStaleExpansions = deleteStaleExpansions(allDefMaps, batch)

        val defMaps = allDefMaps.filter {
            lastUpdatedMacrosAt[it.crate] != it.timestamp
        }
        if (!hasStaleExpansions && defMaps.isEmpty()) return

        val filesToWriteAttributes = createOrDeleteNeededFiles(defMaps, batch)
        applyBatchAndWriteAttributes(batch, filesToWriteAttributes)

        modificationTracker.incModificationCount()
        for (defMap in defMaps) {
            lastUpdatedMacrosAt[defMap.crate] = defMap.timestamp
        }
    }

    private fun createOrDeleteNeededFiles(defMaps: List<CrateDefMap>, batch: MacroExpansionVfsBatch): List<FileAttributes> {
        val expansionSharedCache = MacroExpansionSharedCache.getInstance()
        val filesToWriteAttributes = mutableListOf<FileAttributes>()
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
                val path = batch.createFile(defMap.crate, expansionName, expansion.text, implicit = true)
                filesToWriteAttributes += FileAttributes(path, ranges)
            }
            for (fileName in filesToDelete) {
                val file = existingFiles.getValue(fileName)
                batch.deleteFile(file)
            }
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

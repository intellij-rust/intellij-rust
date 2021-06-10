/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.FeatureState
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.rustFile
import org.rust.lang.core.psi.shouldIndexFile
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.testAssert
import org.rust.openapiext.toPsiFile
import org.rust.stdext.mapNotNullToSet
import org.rust.stdext.mapToSet

/**
 * Calculates new value of [DefMapHolder.shouldRebuild] field.
 * Also clears [DefMapHolder.shouldRecheck] and [DefMapHolder.changedFiles].
 */
fun DefMapHolder.getShouldRebuild(crate: Crate): Boolean {
    checkReadAccessAllowed()
    if (shouldRebuild) return true
    if (!shouldRecheck && changedFiles.isEmpty()) return false
    // If `shouldRebuild == false` then `defMap` was built at least once
    // If `defMap` is `null` then [crate] is weird (e.g. with null `id` or `rootMod`) and can be ignored
    val defMap = defMap ?: return false

    // We can just add all crate files to [changedFiles] if [shouldRecheck],
    // but getting all crate files can be slow
    val changedFilesCopy = if (shouldRecheck) changedFiles.toSet() else emptySet()
    processChangedFiles(crate, defMap) && return true

    if (shouldRecheck) {
        if (isCrateChanged(crate, defMap)) return true
        changedFiles += defMap.getAllChangedFiles(crate.project, ignoredFiles = changedFilesCopy) ?: return true
        shouldRecheck = false

        processChangedFiles(crate, defMap) && return true
    }
    return false
}

private fun CrateDefMap.getAllChangedFiles(project: Project, ignoredFiles: Set<RsFile>): List<RsFile>? {
    val persistentFS = PersistentFS.getInstance()
    return fileInfos.mapNotNull { (fileId, fileInfo) ->
        val file = persistentFS
            .findFileById(fileId)
            ?.toPsiFile(project)
            ?.rustFile
            ?: return null  // file was deleted - should rebuilt DefMap
        file.takeIf {
            it !in ignoredFiles
                && it.modificationStampForResolve != fileInfo.modificationStamp
        }
    }
}

private fun DefMapHolder.processChangedFiles(crate: Crate, defMap: CrateDefMap): Boolean {
    // We are in read action, and [changedFiles] are modified only in write action
    val iterator = changedFiles.iterator()
    /** We use iterator in order to not lose progress if [isFileChanged] throws [ProcessCanceledException] */
    while (iterator.hasNext()) {
        ProgressManager.checkCanceled()
        val file = iterator.next()
        if (isFileChanged(file, defMap, crate)) {
            return true
        } else {
            iterator.remove()
        }
    }
    return false
}

data class CrateMetaData(
    val name: String,
    val edition: CargoWorkspace.Edition,
    private val features: Map<String, FeatureState>,
    private val cfgOptions: CfgOptions?,
    val env: Map<String, String>,
    // TODO: Probably we need to store modificationStamp of DefMap for each dependency
    private val dependencies: Set<CratePersistentId>,
    private val dependenciesNames: Set<String>,
    val procMacroArtifact: CargoWorkspaceData.ProcMacroArtifact?,
) {
    constructor(crate: Crate) : this(
        name = crate.normName,
        edition = crate.edition,
        features = crate.features,
        cfgOptions = crate.cfgOptions,
        env = crate.env,
        dependencies = crate.flatDependencies.mapNotNullToSet { it.id },
        dependenciesNames = crate.dependencies.mapToSet { it.normName },
        procMacroArtifact = crate.procMacroArtifact
    )
}

fun isCrateChanged(crate: Crate, defMap: CrateDefMap): Boolean {
    ProgressManager.checkCanceled()

    val crateRootFile = crate.rootModFile ?: return false
    testAssert(
        { shouldIndexFile(crate.project, crateRootFile) },
        { "isCrateChanged should not be called for crates which are not indexed" }
    )

    return defMap.metaData != CrateMetaData(crate) || defMap.hasAnyMissedFileCreated()
}

private fun CrateDefMap.hasAnyMissedFileCreated(): Boolean {
    val fileManager = VirtualFileManager.getInstance()
    return missedFiles.any { fileManager.findFileByNioPath(it) != null }
}

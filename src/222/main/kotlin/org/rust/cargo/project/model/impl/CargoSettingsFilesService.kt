/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.RsFileType

@Service
class CargoSettingsFilesService(private val project: Project) {

    @Volatile
    private var settingsFilesCache: Map<String, SettingFileType>? = null

    fun collectSettingsFiles(useCache: Boolean): Map<String, SettingFileType> {
        return if (useCache) {
            settingsFilesCache ?: collectSettingsFiles()
        } else {
            collectSettingsFiles()
        }
    }

    private fun collectSettingsFiles(): Map<String, SettingFileType> {
        val result = mutableMapOf<String, SettingFileType>()
        for (cargoProject in project.cargoProjects.allProjects) {
            cargoProject.collectSettingsFiles(result)
        }

        settingsFilesCache = result

        return result
    }

    private fun CargoProject.collectSettingsFiles(out: MutableMap<String, SettingFileType>) {
        val rootPath = rootDir?.path
        if (rootPath != null) {
            out["$rootPath/${CargoConstants.MANIFEST_FILE}"] = SettingFileType.CONFIG
            out["$rootPath/${CargoConstants.LOCK_FILE}"] = SettingFileType.CONFIG
        }

        for (pkg in workspace?.packages.orEmpty().filter { it.origin == PackageOrigin.WORKSPACE }) {
            pkg.contentRoot?.collectSettingsFiles(out)
        }
    }

    private fun VirtualFile.collectSettingsFiles(out: MutableMap<String, SettingFileType>) {
        out["$path/${CargoConstants.MANIFEST_FILE}"] = SettingFileType.CONFIG

        // Here we track only existing implicit target files.
        // It's enough because `com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware.getSettingsFiles`
        // will be called on new file creation by the platform, so we need to provide a list of all possible implicit target files here
        for (targetFileName in IMPLICIT_TARGET_FILES) {
            val path = findFileByRelativePath(targetFileName)?.path ?: continue
            out[path] = SettingFileType.IMPLICIT_TARGET
        }

        for (targetDirName in IMPLICIT_TARGET_DIRS) {
            val dir = findFileByRelativePath(targetDirName) ?: continue
            for (file in VfsUtil.collectChildrenRecursively(dir)) {
                if (file.fileType == RsFileType) {
                    out[file.path] = SettingFileType.IMPLICIT_TARGET
                }
            }
        }
    }

    companion object {
        fun getInstance(project: Project): CargoSettingsFilesService = project.service()

        private val IMPLICIT_TARGET_FILES = listOf(
            "build.rs", "src/main.rs", "src/lib.rs"
        )

        private val IMPLICIT_TARGET_DIRS = listOf(
            "src/bin", "examples", "tests", "benches"
        )
    }

    enum class SettingFileType {
        CONFIG,
        IMPLICIT_TARGET
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.proc.ProcMacroApplicationService
import org.rust.openapiext.isFeatureEnabled

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
            out["$rootPath/${CargoConstants.TOOLCHAIN_FILE}"] = SettingFileType.CONFIG
            out["$rootPath/${CargoConstants.TOOLCHAIN_TOML_FILE}"] = SettingFileType.CONFIG
            out["$rootPath/.cargo/${CargoConstants.CONFIG_FILE}"] = SettingFileType.CONFIG
            out["$rootPath/.cargo/${CargoConstants.CONFIG_TOML_FILE}"] = SettingFileType.CONFIG
        }

        for (pkg in workspace?.packages.orEmpty().filter { it.origin == PackageOrigin.WORKSPACE }) {
            pkg.collectSettingsFiles(out)
        }
    }

    private fun CargoWorkspace.Package.collectSettingsFiles(out: MutableMap<String, SettingFileType>) {
        val root = contentRoot ?: return
        out["${root.path}/${CargoConstants.MANIFEST_FILE}"] = SettingFileType.CONFIG

        // Here we track only existing implicit target files.
        // It's enough because `com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware.getSettingsFiles`
        // will be called on new file creation by the platform, so we need to provide a list of all possible implicit target files here
        for (targetFileName in IMPLICIT_TARGET_FILES) {
            val path = root.findFileByRelativePath(targetFileName)?.path ?: continue
            out[path] = SettingFileType.IMPLICIT_TARGET
        }

        for (targetDirName in IMPLICIT_TARGET_DIRS) {
            val dir = root.findFileByRelativePath(targetDirName) ?: continue
            for (file in VfsUtil.collectChildrenRecursively(dir)) {
                if (file.fileType == RsFileType) {
                    out[file.path] = SettingFileType.IMPLICIT_TARGET
                }
            }
        }

        val (buildScriptFile, settingType) = if (isFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS)) {
            // Ideally, we should add any child module of build script target as config files as well.
            // But it's a quite rare case, so let's implement it separately if it's really needed
            val buildScriptFile = targets.find { it.kind.isCustomBuild }?.crateRoot ?: root.findFileByRelativePath(CargoConstants.BUILD_FILE)
            buildScriptFile to SettingFileType.CONFIG
        } else {
            root.findFileByRelativePath(CargoConstants.BUILD_FILE) to SettingFileType.IMPLICIT_TARGET
        }
        if (buildScriptFile != null) {
            out[buildScriptFile.path] = settingType
        }

        if (ProcMacroApplicationService.isAnyEnabled()) {
            val procMacroLibCrateRoot = targets.find { it.kind.isProcMacro }?.crateRoot
            if (procMacroLibCrateRoot != null) {
                // Ideally, we should add any child module of proc macro lib target as config files as well.
                out[procMacroLibCrateRoot.path] = SettingFileType.CONFIG
            }
        }
    }

    companion object {
        fun getInstance(project: Project): CargoSettingsFilesService = project.service()

        private val IMPLICIT_TARGET_FILES = listOf(
            "src/main.rs", "src/lib.rs"
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

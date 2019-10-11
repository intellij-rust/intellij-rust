/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.openapiext.pathAsPath
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class TestCargoProjectsServiceImpl(project: Project) : CargoProjectsServiceImpl(project) {

    fun createTestProject(rootDir: VirtualFile, ws: CargoWorkspace, rustcInfo: RustcInfo? = null) {
        val manifest = rootDir.pathAsPath.resolve("Cargo.toml")
        val testProject = CargoProjectImpl(manifest, this, ws, null, rustcInfo,
            workspaceStatus = CargoProject.UpdateStatus.UpToDate,
            rustcInfoStatus = if (rustcInfo != null) CargoProject.UpdateStatus.UpToDate else CargoProject.UpdateStatus.NeedsUpdate)
        testProject.setRootDir(rootDir)
        modifyProjectsSync { CompletableFuture.completedFuture(listOf(testProject)) }
    }

    fun setRustcInfo(rustcInfo: RustcInfo) {
        modifyProjectsSync { projects ->
            val updatedProjects = projects.map { it.copy(rustcInfo = rustcInfo, rustcInfoStatus = CargoProject.UpdateStatus.UpToDate) }
            CompletableFuture.completedFuture(updatedProjects)
        }
    }

    fun setEdition(edition: CargoWorkspace.Edition) {
        modifyProjectsSync { projects ->
            val updatedProjects = projects.map { project ->
                val ws = project.workspace?.withEdition(edition)
                project.copy(rawWorkspace = ws)
            }
            CompletableFuture.completedFuture(updatedProjects)
        }
    }

    fun setCfgOptions(cfgOptions: CfgOptions) {
        modifyProjectsSync { projects ->
            val updatedProjects = projects.map { project ->
                val ws = project.workspace?.withCfgOptions(cfgOptions)
                project.copy(rawWorkspace = ws)
            }
            CompletableFuture.completedFuture(updatedProjects)
        }
    }

    private fun modifyProjectsSync(f: (List<CargoProjectImpl>) -> CompletableFuture<List<CargoProjectImpl>>) {
        modifyProjects(f).get(1, TimeUnit.MINUTES) ?: error("Timeout when refreshing a test Cargo project")
    }

    fun discoverAndRefreshSync(): List<CargoProject> {
        val projects = discoverAndRefresh().get(1, TimeUnit.MINUTES)
            ?: error("Timeout when refreshing a test Cargo project")
        if (projects.isEmpty()) error("Failed to update a test Cargo project")
        return projects
    }
}

val Project.testCargoProjects: TestCargoProjectsServiceImpl get() = cargoProjects as TestCargoProjectsServiceImpl

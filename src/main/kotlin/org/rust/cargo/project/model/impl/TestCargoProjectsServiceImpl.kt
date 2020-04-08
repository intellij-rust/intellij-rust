/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.rustPsiManager
import org.rust.openapiext.pathAsPath
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class TestCargoProjectsServiceImpl(project: Project) : CargoProjectsServiceImpl(project) {

    @TestOnly
    fun createTestProject(rootDir: VirtualFile, ws: CargoWorkspace, rustcInfo: RustcInfo? = null) {
        val manifest = rootDir.pathAsPath.resolve("Cargo.toml")
        val testProject = CargoProjectImpl(manifest, this, emptyMap(), ws, null, rustcInfo,
            workspaceStatus = CargoProject.UpdateStatus.UpToDate,
            rustcInfoStatus = if (rustcInfo != null) CargoProject.UpdateStatus.UpToDate else CargoProject.UpdateStatus.NeedsUpdate)
        testProject.setRootDir(rootDir)
        modifyProjectsSync { CompletableFuture.completedFuture(listOf(testProject)) }
    }

    @TestOnly
    fun setRustcInfo(rustcInfo: RustcInfo, parentDisposable: Disposable) {
        val oldValues = mutableMapOf<Path, RustcInfo?>()

        modifyProjectsSync { projects ->
            projects.forEach { oldValues[it.manifest] = it.rustcInfo }
            val updatedProjects = projects.map {
                it.copy(rustcInfo = rustcInfo, rustcInfoStatus = CargoProject.UpdateStatus.UpToDate)
            }
            CompletableFuture.completedFuture(updatedProjects)
        }

        Disposer.register(parentDisposable, Disposable {
            modifyProjectsSync { projects ->
                val updatedProjects = projects.map {
                    it.copy(rustcInfo = oldValues[it.manifest], rustcInfoStatus = CargoProject.UpdateStatus.UpToDate)
                }
                CompletableFuture.completedFuture(updatedProjects)
            }
        })
    }

    @TestOnly
    fun setEdition(edition: CargoWorkspace.Edition, parentDisposable: Disposable) {
        if (edition == CargoWorkspace.Edition.EDITION_2015) return

        setEditionInner(edition)

        Disposer.register(parentDisposable, Disposable {
            setEditionInner(CargoWorkspace.Edition.EDITION_2015)
        })
    }

    private fun setEditionInner(edition: CargoWorkspace.Edition) {
        modifyProjectsSync { projects ->
            val updatedProjects = projects.map { project ->
                val ws = project.workspace?.withEdition(edition)
                project.copy(rawWorkspace = ws)
            }
            CompletableFuture.completedFuture(updatedProjects)
        }
        project.rustPsiManager.incRustStructureModificationCount()
    }

    @TestOnly
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

    @TestOnly
    fun discoverAndRefreshSync(): List<CargoProject> {
        return discoverAndRefresh().get(1, TimeUnit.MINUTES)
            ?: error("Timeout when refreshing a test Cargo project")
    }
}

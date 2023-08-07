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
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.project.workspace.FeatureDep
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.lang.core.psi.rustPsiManager
import org.rust.openapiext.pathAsPath
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class TestCargoProjectsServiceImpl(project: Project) : CargoProjectsServiceImpl(project) {

    @TestOnly
    fun createTestProject(rootDir: VirtualFile, ws: CargoWorkspace, rustcInfo: RustcInfo? = null) {
        val manifest = rootDir.pathAsPath.resolve("Cargo.toml")
        val testProject = CargoProjectImpl(
            manifest, this, UserDisabledFeatures.EMPTY, ws, null, rustcInfo,
            workspaceStatus = CargoProject.UpdateStatus.UpToDate,
            rustcInfoStatus = if (rustcInfo != null) CargoProject.UpdateStatus.UpToDate else CargoProject.UpdateStatus.NeedsUpdate
        )
        testProject.setRootDir(rootDir)
        modifyProjectsSync { CompletableFuture.completedFuture(listOf(testProject)) }
    }

    @TestOnly
    fun removeAllProjects() {
        modifyProjectsSync { CompletableFuture.completedFuture(emptyList()) }
    }

    @TestOnly
    fun setRustcVersion(rustcVersion: RustcVersion, parentDisposable: Disposable) {
        val oldValues = mutableMapOf<Path, RustcInfo?>()

        modifyProjectsSync { projects ->
            projects.forEach { oldValues[it.manifest] = it.rustcInfo }
            val updatedProjects = projects.map {
                val oldRustcInfo = it.rustcInfo ?: RustcInfo("", null)
                val rustcInfo = oldRustcInfo.copy(version = rustcVersion)
                it.copy(rustcInfo = rustcInfo, rustcInfoStatus = CargoProject.UpdateStatus.UpToDate)
            }
            CompletableFuture.completedFuture(updatedProjects)
        }

        Disposer.register(parentDisposable) {
            modifyProjectsSync { projects ->
                val updatedProjects = projects.map {
                    it.copy(rustcInfo = oldValues[it.manifest], rustcInfoStatus = CargoProject.UpdateStatus.UpToDate)
                }
                CompletableFuture.completedFuture(updatedProjects)
            }
        }
    }

    @TestOnly
    fun setEdition(edition: Edition, parentDisposable: Disposable) {
        if (edition == DEFAULT_EDITION_FOR_TESTS) return

        setEditionInner(edition)

        Disposer.register(parentDisposable) {
            setEditionInner(DEFAULT_EDITION_FOR_TESTS)
        }
    }

    @TestOnly
    fun withEdition(edition: Edition, action: () -> Unit) {
        if (edition == DEFAULT_EDITION_FOR_TESTS) return action()

        setEditionInner(edition)
        try {
            action()
        } finally {
            setEditionInner(DEFAULT_EDITION_FOR_TESTS)
        }
    }

    private fun setEditionInner(edition: Edition) {
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
    fun setCfgOptions(cfgOptions: CfgOptions, parentDisposable: Disposable) {
        setCfgOptionsInner(cfgOptions)
        Disposer.register(parentDisposable) {
            setCfgOptionsInner(CfgOptions.DEFAULT)
        }
    }

    private fun setCfgOptionsInner(cfgOptions: CfgOptions) {
        modifyProjectsSync { projects ->
            val updatedProjects = projects.map { project ->
                val ws = project.workspace?.withCfgOptions(cfgOptions)
                project.copy(rawWorkspace = ws)
            }
            CompletableFuture.completedFuture(updatedProjects)
        }
    }

    @TestOnly
    fun setCargoFeatures(features: Map<PackageFeature, List<FeatureDep>>, parentDisposable: Disposable) {
        setCargoFeaturesInner(features)
        Disposer.register(parentDisposable) {
            setCargoFeaturesInner(emptyMap())
        }
    }

    private fun setCargoFeaturesInner(features: Map<PackageFeature, List<FeatureDep>>) {
        modifyProjectsSync { projects ->
            val updatedProjects = projects.map { project ->
                val ws = project.workspace?.withCargoFeatures(features)
                project.copy(rawWorkspace = ws, userDisabledFeatures = UserDisabledFeatures.EMPTY)
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

    @TestOnly
    fun refreshAllProjectsSync(): List<CargoProject> {
        return refreshAllProjects().get(1, TimeUnit.MINUTES)
            ?: error("Timeout when refreshing a test Cargo project")
    }
}

@get:TestOnly
val DEFAULT_EDITION_FOR_TESTS: Edition
    get() {
        val edition = System.getenv("DEFAULT_EDITION_FOR_TESTS") ?: return Edition.EDITION_2021
        return Edition.values().single { it.presentation == edition }
    }

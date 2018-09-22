/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.RustcVersion
import org.rust.ide.notifications.showBalloon
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * [CargoProjectsService] stores a list of `Cargo.toml` file,
 * registered with the current IDE project. Each `Cargo.toml`
 * is represented by a [CargoProject], whose main attribute is
 * `workspace`: a description of a Cargo project acquired from
 * Cargo itself via `cargo metadata` command.
 */
interface CargoProjectsService {
    fun findProjectForFile(file: VirtualFile): CargoProject?
    val allProjects: Collection<CargoProject>
    val hasAtLeastOneValidProject: Boolean

    fun attachCargoProject(manifest: Path): Boolean
    fun detachCargoProject(cargoProject: CargoProject)
    fun refreshAllProjects(): CompletableFuture<List<CargoProject>>
    fun discoverAndRefresh(): CompletableFuture<List<CargoProject>>

    @TestOnly
    fun createTestProject(rootDir: VirtualFile, ws: CargoWorkspace, rustcInfo: RustcInfo? = null)

    @TestOnly
    fun setRustcInfo(rustcInfo: RustcInfo)

    @TestOnly
    fun setEdition(edition: CargoWorkspace.Edition)

    @TestOnly
    fun discoverAndRefreshSync(): List<CargoProject> {
        val projects = discoverAndRefresh().get(1, TimeUnit.MINUTES)
            ?: error("Timeout when refreshing a test Cargo project")
        if (projects.isEmpty()) error("Failed to update a test Cargo project")
        return projects
    }

    companion object {
        val CARGO_PROJECTS_TOPIC: Topic<CargoProjectsListener> = Topic(
            "cargo projects changes",
            CargoProjectsListener::class.java
        )
    }

    interface CargoProjectsListener {
        fun cargoProjectsUpdated(projects: Collection<CargoProject>)
    }
}

val Project.cargoProjects get() = service<CargoProjectsService>()

interface CargoProject {
    val manifest: Path
    val rootDir: VirtualFile?
    val workspaceRootDir: VirtualFile?

    val presentableName: String
    val workspace: CargoWorkspace?

    val rustcInfo: RustcInfo?

    val workspaceStatus: UpdateStatus
    val stdlibStatus: UpdateStatus
    val rustcInfoStatus: UpdateStatus

    val mergedStatus: UpdateStatus get() = workspaceStatus
        .merge(stdlibStatus)
        .merge(rustcInfoStatus)

    sealed class UpdateStatus(private val priority: Int) {
        object UpToDate : UpdateStatus(0)
        object NeedsUpdate : UpdateStatus(1)
        class UpdateFailed(val reason: String) : UpdateStatus(2)

        fun merge(status: UpdateStatus): UpdateStatus = if (priority >= status.priority) this else status
    }
}

data class RustcInfo(val sysroot: String, val version: RustcVersion?)

fun guessAndSetupRustProject(project: Project, explicitRequest: Boolean = false): Boolean {
    if (!explicitRequest) {
        val alreadyTried = run {
            val key = "org.rust.cargo.project.model.PROJECT_DISCOVERY"
            val properties = PropertiesComponent.getInstance(project)
            val alreadyTried = properties.getBoolean(key)
            properties.setValue(key, true)
            alreadyTried
        }
        if (alreadyTried) return false
    }

    val toolchain = project.rustSettings.toolchain
    if (toolchain == null || !toolchain.looksLikeValidToolchain()) {
        discoverToolchain(project)
        return true
    }
    if (!project.cargoProjects.hasAtLeastOneValidProject) {
        project.cargoProjects.discoverAndRefresh()
        return true
    }
    return false
}

private fun discoverToolchain(project: Project) {
    val toolchain = RustToolchain.suggest() ?: return
    ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater

        val oldToolchain = project.rustSettings.toolchain
        if (oldToolchain != null && oldToolchain.looksLikeValidToolchain()) {
            return@invokeLater
        }

        runWriteAction {
            project.rustSettings.data = project.rustSettings.data.copy(toolchain = toolchain)
        }

        val tool = if (toolchain.isRustupAvailable) "rustup" else "Cargo at ${toolchain.presentableLocation}"
        project.showBalloon("Using $tool", NotificationType.INFORMATION)
        project.cargoProjects.discoverAndRefresh()
    }
}

fun ContentEntry.setup(contentRoot: VirtualFile) {
    val makeVfsUrl = { dirName: String -> FileUtil.join(contentRoot.url, dirName) }
    CargoConstants.ProjectLayout.sources.map(makeVfsUrl).forEach {
        addSourceFolder(it, /* test = */ false)
    }
    CargoConstants.ProjectLayout.tests.map(makeVfsUrl).forEach {
        addSourceFolder(it, /* test = */ true)
    }
    addExcludeFolder(makeVfsUrl(CargoConstants.ProjectLayout.target))
}

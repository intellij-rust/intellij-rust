/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.rust.cargo.project.workspace.CargoWorkspace
import java.nio.file.Path
import java.util.concurrent.TimeUnit

interface CargoProject {
    val manifest: Path
    val rootDir: VirtualFile?

    val presentableName: String
    val workspace: CargoWorkspace?

    val workspaceStatus: UpdateStatus
    val stdlibStatus: UpdateStatus

    val mergedStatus: UpdateStatus
        get() = when {
            workspaceStatus is UpdateStatus.UpdateFailed -> workspaceStatus
            stdlibStatus is UpdateStatus.UpdateFailed -> stdlibStatus
            workspaceStatus is UpdateStatus.NeedsUpdate -> workspaceStatus
            else -> stdlibStatus
        }

    sealed class UpdateStatus {
        object NeedsUpdate : UpdateStatus()
        object UpToDate : UpdateStatus()
        class UpdateFailed(val reason: String) : UpdateStatus()
    }
}

interface CargoProjectsService {
    val allProjects: Collection<CargoProject>

    @TestOnly
    fun createTestProject(rootDir: VirtualFile, ws: CargoWorkspace)

    fun discoverAndRefresh(): Promise<List<CargoProject>>

    @TestOnly
    fun discoverAndRefreshSync(): List<CargoProject> {
        val projects = discoverAndRefresh().blockingGet(1, TimeUnit.MINUTES)
            ?: error("Timeout when refreshing a test Cargo project")
        if (projects.isEmpty()) error("Failed to update a test Cargo project")
        return projects
    }

    fun refreshAllProjects(): Promise<List<CargoProject>>

    fun findProjectForFile(file: VirtualFile): CargoProject?

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

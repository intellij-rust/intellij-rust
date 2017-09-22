/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.rust.cargo.project.workspace.CargoWorkspace
import java.nio.file.Path

interface CargoProject {
    val manifest: Path
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

    fun refreshAllProjects()

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

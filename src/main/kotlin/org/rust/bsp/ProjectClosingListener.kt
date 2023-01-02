package org.rust.bsp

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isNewProject
import org.rust.bsp.service.BspConnectionService

class ProjectClosingListener: ProjectManagerListener {

    override fun projectClosing(project: Project) {
        if (project.isNewProject()) {
            return
        }
        runModalTask("Disconnecting...", project = project, cancellable = false) {
            try {
                val connection = project.service<BspConnectionService>()
                connection.disconnect()
            } catch (e: Exception) {
                log.warn("One of the disconnect actions has failed!", e)
            }
        }
    }

    private companion object {
        private val log = logger<ProjectClosingListener>()
    }
}


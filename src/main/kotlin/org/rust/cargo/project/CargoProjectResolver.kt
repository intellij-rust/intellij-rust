package org.rust.cargo.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.rust.cargo.project.settings.CargoExecutionSettings

class CargoProjectResolver : ExternalSystemProjectResolver<CargoExecutionSettings> {

    @Throws(ExternalSystemException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun resolveProjectInfo(id: ExternalSystemTaskId,
                                    projectPath: String,
                                    isPreviewMode: Boolean,
                                    settings: CargoExecutionSettings?,
                                    listener: ExternalSystemTaskNotificationListener): DataNode<ProjectData>? {
        return null
    }

    override fun cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        // TODO(kudinkin): cancel properly
        return false
    }

}


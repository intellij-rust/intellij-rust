package org.rust.cargo.project

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.task.AbstractExternalSystemTaskManager
import org.rust.cargo.project.settings.CargoExecutionSettings

class CargoTaskManager : AbstractExternalSystemTaskManager<CargoExecutionSettings>() {
    @Throws(ExternalSystemException::class)
    override fun executeTasks(
            id: ExternalSystemTaskId,
            taskNames: List<String>,
            projectPath: String,
            settings: CargoExecutionSettings?,
            vmOptions: List<String>,
            scriptParameters: List<String>,
            debuggerSetup: String?,
            listener: ExternalSystemTaskNotificationListener) {

    }

    @Throws(ExternalSystemException::class)
    override fun cancelTask(
            id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        return false
    }
}

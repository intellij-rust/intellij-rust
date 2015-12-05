package org.rust.cargo.project.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.project.Project
import org.rust.cargo.project.CargoProjectSystem

@State(name = "CargoLocalSettings", storages = arrayOf(Storage(file = StoragePathMacros.WORKSPACE_FILE)))
class CargoLocalSettings protected constructor(project: Project) :
        AbstractExternalSystemLocalSettings(CargoProjectSystem.ID, project),
        PersistentStateComponent<AbstractExternalSystemLocalSettings.State> {

    override fun getState(): AbstractExternalSystemLocalSettings.State? {
        val state = AbstractExternalSystemLocalSettings.State()
        fillState(state)
        return state
    }

    companion object {

        fun getInstance(project: Project): CargoLocalSettings {
            return ServiceManager.getService(project, CargoLocalSettings::class.java)
        }
    }
}

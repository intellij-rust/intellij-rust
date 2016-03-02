package org.rust.cargo.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings

class CargoProjectSettings(var cargoHome: String? = null) : ExternalProjectSettings() {

    protected fun copyTo(receiver: CargoProjectSettings) {
        receiver.cargoHome = cargoHome
        super.copyTo(receiver)
    }

    override fun clone(): ExternalProjectSettings {
        val result = CargoProjectSettings()
        copyTo(result)
        return result
    }
}

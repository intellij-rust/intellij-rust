package org.rust.cargo.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.projectRoots.ProjectJdkTable

class CargoProjectSettings(
    var sdkName: String? = null
) : ExternalProjectSettings() {

    protected fun copyTo(receiver: CargoProjectSettings) {
        receiver.sdkName = sdkName
        super.copyTo(receiver)
    }

    override fun clone(): ExternalProjectSettings {
        val result = CargoProjectSettings()
        copyTo(result)
        return result
    }

    val cargoHome: String? get() {
        sdkName ?: return null
        val sdk = ProjectJdkTable.getInstance().findJdk(sdkName)
        return sdk?.homePath
    }
}

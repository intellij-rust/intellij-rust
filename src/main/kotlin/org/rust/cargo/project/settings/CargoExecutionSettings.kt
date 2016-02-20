package org.rust.cargo.project.settings

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import org.rust.cargo.project.RustSdkType

/**
 * Settings required to execute Cargo-specific tasks inside isolated process
 */
class CargoExecutionSettings(
    val cargoPath: String?,
    val sdkName: String?
) : ExternalSystemExecutionSettings() {


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        if (!super.equals(other)) return false

        other as CargoExecutionSettings

        if (cargoPath != other.cargoPath) return false
        if (sdkName != other.sdkName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result += 31 * result + (cargoPath?.hashCode() ?: 0)
        result += 31 * result + (sdkName?.hashCode() ?: 0)
        return result
    }

    companion object {

        fun from(settings: CargoProjectSettings?): CargoExecutionSettings {
            val pathToCargo = settings?.cargoHome?.let {
                RustSdkType.getPathToExecInSDK(it, RustSdkType.CARGO_BINARY_NAME).absolutePath
            }
            return CargoExecutionSettings(pathToCargo, settings?.sdkName)
        }
    }
}

package org.rust.cargo.project.settings

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.projectRoots.SdkType
import org.rust.cargo.project.RustSdkType

/**
 * Settings required to execute Cargo-specific tasks inside isolated process
 */
class CargoExecutionSettings(val cargoPath: String, val features: List<String>) : ExternalSystemExecutionSettings() {

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        if (!super.equals(other)) return false

        other as CargoExecutionSettings

        if (features != other.features) return false

        return true
    }

    override fun hashCode(): Int{
        var result = super.hashCode()
            result += 31 * result + cargoPath.hashCode()
            result += 31 * result + features.hashCode()

        return result
    }

    companion object {

        fun from(settings: CargoProjectSettings): CargoExecutionSettings {
            val sdk = SdkType.findInstance(RustSdkType::class.java)

            return CargoExecutionSettings(
                sdk.getPathToExecInSDK(settings.cargoHome!!, RustSdkType.CARGO_BINARY_NAME).absolutePath,
                settings.features
            )
        }

    }
}

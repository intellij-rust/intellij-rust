package org.rust.cargo.project.settings

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings

class CargoExecutionSettings(var features: List<String>) : ExternalSystemExecutionSettings() {

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
            result += 31 * result + features.hashCode()

        return result
    }
}

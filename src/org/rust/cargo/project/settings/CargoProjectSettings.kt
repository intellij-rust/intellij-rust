package org.rust.cargo.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings

class CargoProjectSettings : ExternalProjectSettings() {
    var features = emptyList<String>()

    protected fun copyTo(receiver: CargoProjectSettings) {
        receiver.features = features
        super.copyTo(receiver)
    }

    override fun clone(): ExternalProjectSettings {
        val result = CargoProjectSettings()
        copyTo(result)
        return result
    }

    fun setCargoHome(path: String?) {
        throw NotImplementedError()
    }

    fun getCargoHome(): String {
        throw NotImplementedError()
    }

    fun setDistributionType(type: Companion.Distribution) {
        throw NotImplementedError()
    }

    fun getDistributionType(): Companion.Distribution? {
        throw NotImplementedError()
    }

    companion object {

        enum class Distribution {
            /**
             * Employ local-distribution of the Cargo
             */
            LOCAL
        }

    }
}

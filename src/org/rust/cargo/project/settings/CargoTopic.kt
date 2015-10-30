package org.rust.cargo.project.settings

import com.intellij.util.messages.Topic

class CargoTopic private constructor() :
        Topic<CargoProjectSettingsListener>("Cargo-specific settings", CargoProjectSettingsListener::class.java) {
    companion object {
        val INSTANCE = CargoTopic()
    }
}

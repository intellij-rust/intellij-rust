package org.rust.cargo.project.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "Rust", storages = arrayOf(Storage(id = "other", file = StoragePathMacros.APP_CONFIG + "Rust.xml")))
class RustConfigService : PersistentStateComponent<RustConfig> {
    private val config = RustConfig()

    override fun getState(): RustConfig {
        return config
    }

    override fun loadState(config: RustConfig) {
        XmlSerializerUtil.copyBean(config, this.config)
    }
}

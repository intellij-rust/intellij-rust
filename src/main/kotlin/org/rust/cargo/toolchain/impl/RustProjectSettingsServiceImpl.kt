package org.rust.cargo.toolchain.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import org.rust.cargo.toolchain.RustProjectSettingsService
import org.rust.cargo.toolchain.RustToolchain

@State(
    name = "RustProjectSettings",
    storages = arrayOf(Storage(file = StoragePathMacros.PROJECT_FILE))
)
class RustProjectSettingsServiceImpl : PersistentStateComponent<RustProjectSettingsServiceImpl>, RustProjectSettingsService {
    @Attribute
    private var toolchainHomeDirectory: String? = null

    override fun getState(): RustProjectSettingsServiceImpl? = this

    override fun loadState(state: RustProjectSettingsServiceImpl) {
        XmlSerializerUtil.copyBean(state, this)
    }

    @get:Transient
    override var toolchain: RustToolchain?
        get() = toolchainHomeDirectory?.let { RustToolchain(it) }
        set(value) {
            toolchainHomeDirectory = value?.homeDirectory
        }
}


package org.rust.cargo.project.settings

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.rust.cargo.toolchain.RustToolchain

interface RustProjectSettingsService {
    data class Data(
        val toolchain: RustToolchain?,
        val autoUpdateEnabled: Boolean
    )

    var data: Data

    val toolchain: RustToolchain? get() = data.toolchain
    val autoUpdateEnabled: Boolean get() = data.autoUpdateEnabled

    /*
     * Show a dialog for toolchain configuration
     */
    fun configureToolchain()

    companion object {
        val TOOLCHAIN_TOPIC: Topic<ToolchainListener> = Topic(
            "toolchain changes",
            ToolchainListener::class.java
        )
    }

    interface ToolchainListener {
        fun toolchainChanged()
    }
}

val Project.rustSettings: RustProjectSettingsService
    get() = ServiceManager.getService(this, RustProjectSettingsService::class.java)
        ?: error("Failed to get RustProjectSettingsService for $this")

val Project.toolchain: RustToolchain? get() = rustSettings.toolchain

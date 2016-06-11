package org.rust.cargo.project.settings

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.utils.service

interface RustProjectSettingsService {
    var toolchain: RustToolchain?
    var autoUpdateEnabled: Boolean

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
        fun toolchainChanged(newToolchain: RustToolchain?)
    }
}

val Project.rustSettings: RustProjectSettingsService get() = service()
val Project.toolchain: RustToolchain? get() = rustSettings.toolchain


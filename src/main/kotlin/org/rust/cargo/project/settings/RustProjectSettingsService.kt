/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.rust.cargo.toolchain.RustToolchain

interface RustProjectSettingsService {
    data class Data(
        val toolchain: RustToolchain?,
        val autoUpdateEnabled: Boolean,
        // Usually, we use `rustup` to find stdlib automatically,
        // but if one does not use rustup, it's possible to
        // provide path to stdlib explicitly.
        val explicitPathToStdlib: String?,
        val useCargoCheckForBuild: Boolean,
        val useCargoCheckAnnotator: Boolean
    )

    var data: Data

    val toolchain: RustToolchain? get() = data.toolchain
    var explicitPathToStdlib: String?
        get() = data.explicitPathToStdlib
        set(value) {
            data = data.copy(explicitPathToStdlib = value)
        }
    val autoUpdateEnabled: Boolean get() = data.autoUpdateEnabled
    val useCargoCheckForBuild: Boolean get() = data.useCargoCheckForBuild
    val useCargoCheckAnnotator: Boolean get() = data.useCargoCheckAnnotator

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

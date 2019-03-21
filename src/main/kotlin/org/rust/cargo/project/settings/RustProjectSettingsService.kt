/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Transient
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.toolchain.RustToolchain
import java.nio.file.Paths

interface RustProjectSettingsService {

    data class State(
        var version: Int? = null,
        var toolchainHomeDirectory: String? = null,
        var autoUpdateEnabled: Boolean = true,
        // Usually, we use `rustup` to find stdlib automatically,
        // but if one does not use rustup, it's possible to
        // provide path to stdlib explicitly.
        var explicitPathToStdlib: String? = null,
        var externalLinter: ExternalLinter = ExternalLinter.DEFAULT,
        var runExternalLinterOnTheFly: Boolean = false,
        var externalLinterArguments: String = "",
        var compileAllTargets: Boolean = true,
        var useOffline: Boolean = false,
        var macroExpansionEngine: MacroExpansionEngine = MacroExpansionEngine.OLD,
        var showTestToolWindow: Boolean = true,
        var doctestInjectionEnabled: Boolean = true,
        var runRustfmtOnSave: Boolean = false,
        var useSkipChildren: Boolean = false
    ) {
        @get:Transient
        @set:Transient
        var toolchain: RustToolchain?
            get() = toolchainHomeDirectory?.let { RustToolchain(Paths.get(it)) }
            set(value) {
                toolchainHomeDirectory = value?.location?.systemIndependentPath
            }
    }

    enum class MacroExpansionEngine {
        DISABLED, OLD, NEW
    }

    /**
     * Allows to modify settings.
     * After setting change,
     */
    fun modify(action: (State) -> Unit)

    val version: Int?
    val toolchain: RustToolchain?
    val explicitPathToStdlib: String?
    val autoUpdateEnabled: Boolean
    val externalLinter: ExternalLinter
    val runExternalLinterOnTheFly: Boolean
    val externalLinterArguments: String
    val compileAllTargets: Boolean
    val useOffline: Boolean
    val macroExpansionEngine: MacroExpansionEngine
    val showTestToolWindow: Boolean
    val doctestInjectionEnabled: Boolean
    val runRustfmtOnSave: Boolean
    val useSkipChildren: Boolean

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

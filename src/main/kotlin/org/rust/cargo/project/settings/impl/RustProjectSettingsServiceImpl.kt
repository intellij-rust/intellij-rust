/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import org.rust.cargo.project.configurable.RustProjectConfigurable
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.toolchain.RustToolchain
import java.nio.file.Paths

@State(name = "RustProjectSettings")
class RustProjectSettingsServiceImpl(
    private val project: Project
) : PersistentStateComponent<RustProjectSettingsServiceImpl.State>, RustProjectSettingsService {
    @Volatile
    private var state: State = State()

    data class State(
        var toolchainHomeDirectory: String? = null,
        var autoUpdateEnabled: Boolean = true,
        var explicitPathToStdlib: String? = null,
        var useCargoCheckForBuild: Boolean = true,
        var useCargoCheckAnnotator: Boolean = false
    )

    override fun getState(): State = state

    override fun loadState(newState: State) {
        state = newState
    }

    override fun configureToolchain() {
        ShowSettingsUtil.getInstance().editConfigurable(project, RustProjectConfigurable(project))
    }

    override var data: RustProjectSettingsService.Data
        get() {
            state = state
            return RustProjectSettingsService.Data(
                toolchain = state.toolchainHomeDirectory?.let { RustToolchain(Paths.get(it)) },
                autoUpdateEnabled = state.autoUpdateEnabled,
                explicitPathToStdlib = state.explicitPathToStdlib,
                useCargoCheckForBuild = state.useCargoCheckForBuild,
                useCargoCheckAnnotator = state.useCargoCheckAnnotator
            )
        }
        set(value) {
            val newState = State(
                toolchainHomeDirectory = value.toolchain?.location?.systemIndependentPath,
                autoUpdateEnabled = value.autoUpdateEnabled,
                explicitPathToStdlib = value.explicitPathToStdlib,
                useCargoCheckForBuild = value.useCargoCheckForBuild,
                useCargoCheckAnnotator = value.useCargoCheckAnnotator
            )
            if (state != newState) {
                state = newState
                notifyToolchainChanged()
            }
        }

    private fun notifyToolchainChanged() {
        project.messageBus.syncPublisher(RustProjectSettingsService.TOOLCHAIN_TOPIC).toolchainChanged()
    }
}


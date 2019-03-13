/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.rust.cargo.project.configurable.RsProjectConfigurable
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.RustProjectSettingsService.State
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.toolchain.RustToolchain

@com.intellij.openapi.components.State(name = "RustProjectSettings")
class RustProjectSettingsServiceImpl(
    private val project: Project
) : PersistentStateComponent<State>, RustProjectSettingsService {
    @Volatile
    private var state: State = State()

    override val toolchain: RustToolchain? get() = state.toolchain
    override val explicitPathToStdlib: String? get() = state.explicitPathToStdlib
    override val autoUpdateEnabled: Boolean get() = state.autoUpdateEnabled
    override val externalLinter: ExternalLinter get() = state.externalLinter
    override val runExternalLinterOnTheFly: Boolean get() = state.runExternalLinterOnTheFly
    override val externalLinterArguments: String get() = state.externalLinterArguments
    override val compileAllTargets: Boolean get() = state.compileAllTargets
    override val useOffline: Boolean get() = state.useOffline
    override val expandMacros: Boolean get() = state.expandMacros
    override val showTestToolWindow: Boolean get() = state.showTestToolWindow
    override val doctestInjectionEnabled: Boolean get() = state.doctestInjectionEnabled
    override val useSkipChildren: Boolean get() = state.useSkipChildren

    override fun getState(): State = state

    override fun loadState(newState: State) {
        state = newState
    }

    override fun modify(action: (State) -> Unit) {
        val copy = state.copy()
        action(copy)
        if (state != copy) {
            state = copy.copy()
            notifyToolchainChanged()
        }
    }

    override fun configureToolchain() {
        ShowSettingsUtil.getInstance().editConfigurable(project, RsProjectConfigurable(project))
    }

    private fun notifyToolchainChanged() {
        project.messageBus.syncPublisher(RustProjectSettingsService.TOOLCHAIN_TOPIC).toolchainChanged()
    }
}

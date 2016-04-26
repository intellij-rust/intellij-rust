package org.rust.cargo.project.settings.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.configurable.RustProjectConfigurable
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.getComponentOrThrow
import org.rust.cargo.util.getModules
import org.rust.cargo.util.getServiceOrThrow

@State(
    name = "RustProjectSettings",
    storages = arrayOf(Storage(file = StoragePathMacros.PROJECT_FILE))
)
class RustProjectSettingsServiceImpl(
    private val project: Project
) : PersistentStateComponent<RustProjectSettingsServiceImpl.State>, RustProjectSettingsService {
    private var state: State = State()

    data class State(
        var toolchainHomeDirectory: String? = null,
        var autoUpdateEnabled: Boolean = true
    )

    override fun getState(): State = state

    override fun loadState(newState: State) {
        state = newState
    }

    override fun configureToolchain() {
        ShowSettingsUtil.getInstance().editConfigurable(project, RustProjectConfigurable(project))
    }

    override var autoUpdateEnabled: Boolean
        get() = state.autoUpdateEnabled
        set(value) {
            state.autoUpdateEnabled = value
        }

    override var toolchain: RustToolchain?
        get() = state.toolchainHomeDirectory?.let { RustToolchain(it) }
        set(value) {
            if (state.toolchainHomeDirectory != value?.location) {

                if (value != null) {
                    for (module in project.getModules()) {
                        module.getComponentOrThrow<CargoProjectWorkspace>().requestUpdateUsing(value)
                    }
                }

                state.toolchainHomeDirectory = value?.location
                project.messageBus.syncPublisher(RustProjectSettingsService.TOOLCHAIN_TOPIC).toolchainChanged(value)
            }
        }
}


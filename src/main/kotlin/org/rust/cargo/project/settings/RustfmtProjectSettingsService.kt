/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.rust.cargo.project.settings.RustfmtProjectSettingsService.RustfmtProjectSettings
import org.rust.cargo.toolchain.RustChannel

val Project.rustfmtSettings: RustfmtProjectSettingsService
    get() = service<RustfmtProjectSettingsService>()

private const val SERVICE_NAME: String = "RustfmtProjectSettings"

@State(name = SERVICE_NAME, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RustfmtProjectSettingsService(
    project: Project
) : RsProjectSettingsServiceBase<RustfmtProjectSettings>(project, RustfmtProjectSettings()) {
    val additionalArguments: String get() = state.additionalArguments
    val channel: RustChannel get() = state.channel
    val envs: Map<String, String> get() = state.envs
    val useRustfmt: Boolean get() = state.useRustfmt
    val runRustfmtOnSave: Boolean get() = state.runRustfmtOnSave

    class RustfmtProjectSettings : RsProjectSettingsBase<RustfmtProjectSettings>() {
        var additionalArguments by property("") { it.isEmpty() }
        var channel by enum(RustChannel.DEFAULT)
        var envs by map<String, String>()
        var useRustfmt by property(false)
        var runRustfmtOnSave by property(false)

        override fun copy(): RustfmtProjectSettings {
            val state = RustfmtProjectSettings()
            state.copyFrom(this)
            return state
        }
    }

    override fun createSettingsChangedEvent(
        oldEvent: RustfmtProjectSettings,
        newEvent: RustfmtProjectSettings
    ): SettingsChangedEvent = SettingsChangedEvent(oldEvent, newEvent)

    class SettingsChangedEvent(
        oldState: RustfmtProjectSettings,
        newState: RustfmtProjectSettings
    ) : SettingsChangedEventBase<RustfmtProjectSettings>(oldState, newState)
}

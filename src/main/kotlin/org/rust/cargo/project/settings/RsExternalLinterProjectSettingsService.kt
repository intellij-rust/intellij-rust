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
import org.rust.cargo.project.settings.RsExternalLinterProjectSettingsService.RsExternalLinterProjectSettings
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.toolchain.RustChannel

val Project.externalLinterSettings: RsExternalLinterProjectSettingsService
    get() = service<RsExternalLinterProjectSettingsService>()

private const val SERVICE_NAME: String = "RsExternalLinterProjectSettings"

@State(name = SERVICE_NAME, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RsExternalLinterProjectSettingsService(
    project: Project
) : RsProjectSettingsServiceBase<RsExternalLinterProjectSettings>(project, RsExternalLinterProjectSettings()) {
    val tool: ExternalLinter get() = state.tool
    val additionalArguments: String get() = state.additionalArguments
    val channel: RustChannel get() = state.channel
    val envs: Map<String, String> get() = state.envs
    val runOnTheFly: Boolean get() = state.runOnTheFly

    override fun noStateLoaded() {
        val rustSettings = project.rustSettings
        state.tool = rustSettings.state.externalLinter
        rustSettings.state.externalLinter = ExternalLinter.DEFAULT
        state.additionalArguments = rustSettings.state.externalLinterArguments
        rustSettings.state.externalLinterArguments = ""
        state.runOnTheFly = rustSettings.state.runExternalLinterOnTheFly
        rustSettings.state.runExternalLinterOnTheFly = false
    }

    class RsExternalLinterProjectSettings : RsProjectSettingsBase<RsExternalLinterProjectSettings>() {
        @AffectsHighlighting
        var tool by enum(ExternalLinter.DEFAULT)

        @AffectsHighlighting
        var additionalArguments by property("") { it.isEmpty() }

        @AffectsHighlighting
        var channel by enum(RustChannel.DEFAULT)
        @AffectsHighlighting
        var envs by map<String, String>()
        @AffectsHighlighting
        var runOnTheFly by property(false)

        override fun copy(): RsExternalLinterProjectSettings {
            val state = RsExternalLinterProjectSettings()
            state.copyFrom(this)
            return state
        }
    }

    override fun createSettingsChangedEvent(
        oldEvent: RsExternalLinterProjectSettings,
        newEvent: RsExternalLinterProjectSettings
    ): SettingsChangedEvent = SettingsChangedEvent(oldEvent, newEvent)

    class SettingsChangedEvent(
        oldState: RsExternalLinterProjectSettings,
        newState: RsExternalLinterProjectSettings
    ) : SettingsChangedEventBase<RsExternalLinterProjectSettings>(oldState, newState)
}

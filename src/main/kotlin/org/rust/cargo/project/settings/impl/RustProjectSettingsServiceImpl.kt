/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.impl

import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer.deserializeInto
import org.jdom.Element
import org.rust.cargo.project.configurable.RsProjectConfigurable
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.RustProjectSettingsService.MacroExpansionEngine
import org.rust.cargo.project.settings.RustProjectSettingsService.State
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.project.settings.RustProjectSettingsService.FeaturesSetting
import org.rust.cargo.toolchain.RustToolchain

private const val serviceName: String = "RustProjectSettings"

@com.intellij.openapi.components.State(name = serviceName, storages = [
    Storage(StoragePathMacros.WORKSPACE_FILE),
    Storage("misc.xml", deprecated = true)
])
class RustProjectSettingsServiceImpl(
    private val project: Project
) : PersistentStateComponent<Element>, RustProjectSettingsService {
    @Volatile
    private var state: State = State()

    override val version: Int? get() = state.version
    override val toolchain: RustToolchain? get() = state.toolchain
    override val autoUpdateEnabled: Boolean get() = state.autoUpdateEnabled
    override val explicitPathToStdlib: String? get() = state.explicitPathToStdlib
    override val externalLinter: ExternalLinter get() = state.externalLinter
    override val runExternalLinterOnTheFly: Boolean get() = state.runExternalLinterOnTheFly
    override val externalLinterArguments: String get() = state.externalLinterArguments
    override val compileAllTargets: Boolean get() = state.compileAllTargets
    override val cargoFeatures: FeaturesSetting get() = state.cargoFeatures
    override val cargoFeaturesAdditional: List<String> get() = state.cargoFeaturesAdditional
    override val useOffline: Boolean get() = state.useOffline
    override val macroExpansionEngine: MacroExpansionEngine get() = state.macroExpansionEngine
    override val showTestToolWindow: Boolean get() = state.showTestToolWindow
    override val doctestInjectionEnabled: Boolean get() = state.doctestInjectionEnabled
    override val runRustfmtOnSave: Boolean get() = state.runRustfmtOnSave
    override val useSkipChildren: Boolean get() = state.useSkipChildren

    override fun getState(): Element {
        val element = Element(serviceName)
        serializeObjectInto(state, element)
        return element
    }

    override fun loadState(element: Element) {
        val rawState = element.clone()
        rawState.updateToCurrentVersion()
        deserializeInto(state, rawState)
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

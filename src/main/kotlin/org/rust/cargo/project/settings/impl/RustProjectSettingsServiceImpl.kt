/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.util.xmlb.XmlSerializer.deserializeInto
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.configurable.RsProjectConfigurable
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.RustProjectSettingsService.*
import org.rust.cargo.project.settings.RustProjectSettingsService.Companion.RUST_SETTINGS_TOPIC
import org.rust.cargo.toolchain.ExternalLinter
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
    override val useOffline: Boolean get() = state.useOffline
    override val macroExpansionEngine: MacroExpansionEngine get() = state.macroExpansionEngine
    override val doctestInjectionEnabled: Boolean get() = state.doctestInjectionEnabled
    override val useRustfmt: Boolean get() = state.useRustfmt
    override val runRustfmtOnSave: Boolean get() = state.runRustfmtOnSave

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
        val newState = state.copy().also(action)
        if (state != newState) {
            val oldState = state
            state = newState.copy()
            notifySettingsChanged(oldState, newState)
        }
    }

    @TestOnly
    override fun modifyTemporary(parentDisposable: Disposable, action: (State) -> Unit) {
        val newState = state.copy().also(action)
        if (state != newState) {
            val oldState = state
            state = newState.copy()
            notifySettingsChanged(oldState, newState)

            Disposer.register(parentDisposable) {
                state = oldState
            }
        }
    }

    override fun configureToolchain() {
        ShowSettingsUtil.getInstance().editConfigurable(project, RsProjectConfigurable(project))
    }

    private fun notifySettingsChanged(oldState: State, newState: State) {
        val event = RustSettingsChangedEvent(oldState, newState)
        project.messageBus.syncPublisher(RUST_SETTINGS_TOPIC).rustSettingsChanged(event)

        if (event.isChanged(State::doctestInjectionEnabled)) {
            // flush injection cache
            (PsiManager.getInstance(project).modificationTracker as PsiModificationTrackerImpl).incCounter()
        }
        if (event.affectsHighlighting) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}

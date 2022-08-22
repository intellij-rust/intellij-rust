/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.settings.RustfmtProjectSettingsService.RustfmtState
import org.rust.cargo.toolchain.RustChannel

val Project.rustfmtSettings: RustfmtProjectSettingsService get() = service()

private const val serviceName: String = "RustfmtProjectSettings"

@State(name = serviceName, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RustfmtProjectSettingsService(
    private val project: Project
) : SimplePersistentStateComponent<RustfmtState>(RustfmtState()) {

    // BACKCOMPAT: 2023.1
    override fun loadState(state: RustfmtState) {
        if (state.runRustfmtOnSave) {
            PropertiesComponent.getInstance(project).setValue("format.on.save", true)
            state.runRustfmtOnSave = false
        }
        super.loadState(state)
    }

    @TestOnly
    fun modifyTemporary(parentDisposable: Disposable, action: (RustfmtState) -> Unit) {
        val oldState = state
        loadState(oldState.copy().also(action))
        Disposer.register(parentDisposable) {
            loadState(oldState)
        }
    }

    class RustfmtState : BaseState() {
        var version by property(0)
        var additionalArguments by property("") { it.isEmpty() }
        var channel by enum(RustChannel.DEFAULT)
        var envs by map<String, String>()
        var useRustfmt by property(false)
        // BACKCOMPAT: 2023.1
        var runRustfmtOnSave by property(false)

        fun copy(): RustfmtState {
            val state = RustfmtState()
            state.copyFrom(this)
            return state
        }
    }
}

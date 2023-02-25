/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.settings.RustfmtProjectSettingsService.RustfmtState
import org.rust.cargo.toolchain.RustChannel

val Project.rustfmtSettings: RustfmtProjectSettingsService get() = service()

private const val SERVICE_NAME: String = "RustfmtProjectSettings"

@State(name = SERVICE_NAME, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RustfmtProjectSettingsService(
    @Suppress("unused")
    private val project: Project
) : SimplePersistentStateComponent<RustfmtState>(RustfmtState()) {

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
        var runRustfmtOnSave by property(false)

        fun copy(): RustfmtState {
            val state = RustfmtState()
            state.copyFrom(this)
            return state
        }
    }
}

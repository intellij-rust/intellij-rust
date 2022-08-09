/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

class RsLanguageRuntimeConfiguration : LanguageRuntimeConfiguration(RsLanguageRuntimeType.TYPE_ID),
                                       PersistentStateComponent<RsLanguageRuntimeConfiguration.MyState> {
    var rustcPath: String = ""
    var rustcVersion: String = ""

    var cargoPath: String = ""
    var cargoVersion: String = ""

    var localBuildArgs: String = ""

    override fun getState() = MyState().also {
        it.rustcPath = this.rustcPath
        it.rustcVersion = this.rustcVersion

        it.cargoPath = this.cargoPath
        it.cargoVersion = this.cargoVersion

        it.localBuildArgs = this.localBuildArgs
    }

    override fun loadState(state: MyState) {
        this.rustcPath = state.rustcPath.orEmpty()
        this.rustcVersion = state.rustcVersion.orEmpty()

        this.cargoPath = state.cargoPath.orEmpty()
        this.cargoVersion = state.cargoVersion.orEmpty()

        this.localBuildArgs = state.localBuildArgs.orEmpty()
    }

    class MyState : BaseState() {
        var rustcPath by string()
        var rustcVersion by string()

        var cargoPath by string()
        var cargoVersion by string()

        var localBuildArgs by string()
    }
}

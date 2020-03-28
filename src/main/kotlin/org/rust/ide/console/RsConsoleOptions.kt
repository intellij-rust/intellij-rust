/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(name = "RsConsoleOptions", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RsConsoleOptions : PersistentStateComponent<RsConsoleOptions> {

    var showVariables: Boolean = true

    override fun getState(): RsConsoleOptions = this

    override fun loadState(state: RsConsoleOptions) {
        showVariables = state.showVariables
    }

    companion object {
        fun getInstance(project: Project): RsConsoleOptions =
            ServiceManager.getService(project, RsConsoleOptions::class.java)
    }
}

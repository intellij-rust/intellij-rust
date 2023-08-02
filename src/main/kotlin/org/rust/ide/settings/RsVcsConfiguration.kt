/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(name = "RsVcsConfiguration", storages = [Storage("rust.xml")])
class RsVcsConfiguration : SimplePersistentStateComponent<RsVcsConfiguration.State>(State()) {

    class State : BaseState() {
        var rustFmt: Boolean by property(false) { !it }
    }

    companion object {
        fun getInstance(project: Project): RsVcsConfiguration = project.service()
    }
}

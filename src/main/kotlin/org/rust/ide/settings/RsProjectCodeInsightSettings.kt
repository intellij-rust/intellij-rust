/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(name = "RsProjectCodeInsightSettings", storages = [Storage("rust.xml")])
class RsProjectCodeInsightSettings : SimplePersistentStateComponent<RsProjectCodeInsightSettings.State>(State()) {

    class State : BaseState() {
        var excludedPaths: Array<ExcludedPath> by property(arrayOf(), Array<ExcludedPath>::isEmpty)
    }

    companion object {
        fun getInstance(project: Project): RsProjectCodeInsightSettings = project.service()
    }
}

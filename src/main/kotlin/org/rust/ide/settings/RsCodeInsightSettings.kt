/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "RsCodeInsightSettings", storages = [Storage("rust.xml")])
class RsCodeInsightSettings : PersistentStateComponent<RsCodeInsightSettings> {

    var showImportPopup: Boolean = false
    var addTraitImport: Boolean = true

    override fun getState(): RsCodeInsightSettings = this

    override fun loadState(state: RsCodeInsightSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): RsCodeInsightSettings = ServiceManager.getService(RsCodeInsightSettings::class.java)
    }
}

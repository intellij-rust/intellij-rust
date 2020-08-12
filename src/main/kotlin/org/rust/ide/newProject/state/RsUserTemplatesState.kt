/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "RsUserTemplatesState", storages = [Storage("rust.usertemplates.xml")])
class RsUserTemplatesState : PersistentStateComponent<RsUserTemplatesState> {

    var templates = mutableListOf<RsUserTemplate>()

    override fun getState(): RsUserTemplatesState = this

    override fun loadState(state: RsUserTemplatesState) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        val instance: RsUserTemplatesState
            get() = ServiceManager.getService(RsUserTemplatesState::class.java)
    }
}

data class RsUserTemplate(var name: String = "", var url: String = "")

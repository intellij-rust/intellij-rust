/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.folding.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import org.rust.ide.folding.RsCodeFoldingSettings

@State(name = "RsCodeFoldingSettings", storages = arrayOf(Storage("editor.codeinsight.xml")))
class RsCodeFoldingSettingsImpl : RsCodeFoldingSettings(), PersistentStateComponent<RsCodeFoldingSettingsImpl> {

    override var collapsibleOneLineMethods: Boolean = true

    override fun getState(): RsCodeFoldingSettingsImpl = this

    override fun loadState(state: RsCodeFoldingSettingsImpl) = XmlSerializerUtil.copyBean(state, this)

}

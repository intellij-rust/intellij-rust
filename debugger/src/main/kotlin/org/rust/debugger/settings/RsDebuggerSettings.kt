/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.options.Configurable
import com.intellij.util.PlatformUtils
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.xdebugger.settings.DebuggerSettingsCategory
import com.intellij.xdebugger.settings.XDebuggerSettings

class RsDebuggerSettings : XDebuggerSettings<RsDebuggerSettings>("Rust") {

    var isRendersEnabled: Boolean = true

    override fun getState(): RsDebuggerSettings = this

    override fun loadState(state: RsDebuggerSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun createConfigurables(category: DebuggerSettingsCategory): Collection<Configurable> {
        return if (category == DebuggerSettingsCategory.DATA_VIEWS) {
            listOf(RsDebuggerSettingsConfigurable(this))
        } else {
            listOf()
        }
    }

    override fun isTargetedToProduct(configurable: Configurable): Boolean =
        PlatformUtils.isCLion() && configurable.displayName == RsDebuggerSettingsConfigurable.DISPLAY_NAME

    companion object {
        @JvmStatic
        fun getInstance(): RsDebuggerSettings = getInstance(RsDebuggerSettings::class.java)
    }
}

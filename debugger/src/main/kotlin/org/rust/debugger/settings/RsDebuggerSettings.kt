/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.SimpleConfigurable
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.xdebugger.settings.DebuggerSettingsCategory
import com.intellij.xdebugger.settings.XDebuggerSettings
import org.rust.debugger.GDBRenderers
import org.rust.debugger.LLDBRenderers

class RsDebuggerSettings : XDebuggerSettings<RsDebuggerSettings>("Rust") {

    var lldbRenderers: LLDBRenderers = LLDBRenderers.DEFAULT
    var gdbRenderers: GDBRenderers = GDBRenderers.DEFAULT

    var lldbPath: String? = null
    var downloadAutomatically: Boolean = false

    override fun getState(): RsDebuggerSettings = this

    override fun loadState(state: RsDebuggerSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun createConfigurables(category: DebuggerSettingsCategory): Collection<Configurable> {
        val configurable = when (category) {
            DebuggerSettingsCategory.DATA_VIEWS -> createDataViewConfigurable()
            DebuggerSettingsCategory.GENERAL -> if (needToShowToolchainSettings) createToolchainConfigurable() else null
            else -> null
        }
        return listOfNotNull(configurable)
    }

    private fun createDataViewConfigurable(): Configurable {
        return SimpleConfigurable.create(
            DATA_VIEW_ID,
            "Rust",
            RsDebuggerDataViewConfigurableUi::class.java,
            ::getInstance
        )
    }

    private fun createToolchainConfigurable(): Configurable? {
        return SimpleConfigurable.create(
            TOOLCHAIN_ID,
            "Rust",
            RsDebuggerToolchainConfigurableUi::class.java,
            Companion::getInstance
        )
    }

    override fun isTargetedToProduct(configurable: Configurable): Boolean {
        if (configurable !is SearchableConfigurable) return false
        return when (configurable.id) {
            TOOLCHAIN_ID -> needToShowToolchainSettings
            DATA_VIEW_ID -> true
            else -> false
        }
    }

    private val needToShowToolchainSettings: Boolean
        get() {
            return !PlatformUtils.isCLion() && (SystemInfo.isLinux || SystemInfo.isMac)
        }

    companion object {
        @JvmStatic
        fun getInstance(): RsDebuggerSettings = getInstance(RsDebuggerSettings::class.java)

        const val TOOLCHAIN_ID: String = "Debugger.Rust.Toolchain"
        const val DATA_VIEW_ID: String = "Debugger.Rust.DataView"
    }
}

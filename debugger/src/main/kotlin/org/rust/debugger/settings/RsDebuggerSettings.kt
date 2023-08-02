/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.SimpleConfigurable
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.xdebugger.settings.DebuggerSettingsCategory
import com.intellij.xdebugger.settings.XDebuggerSettings
import org.rust.debugger.DebuggerKind
import org.rust.debugger.GDBRenderers
import org.rust.debugger.LLDBRenderers
import org.rust.debugger.RsDebuggerBundle

class RsDebuggerSettings : XDebuggerSettings<RsDebuggerSettings>("Rust") {

    var lldbRenderers: LLDBRenderers = LLDBRenderers.DEFAULT
    var gdbRenderers: GDBRenderers = GDBRenderers.DEFAULT

    var debuggerKind: DebuggerKind = DebuggerKind.LLDB

    var downloadAutomatically: Boolean = false

    var breakOnPanic: Boolean = true
    var skipStdlibInStepping: Boolean = false

    var decorateMsvcTypeNames: Boolean = true

    override fun getState(): RsDebuggerSettings = this

    override fun loadState(state: RsDebuggerSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun createConfigurables(category: DebuggerSettingsCategory): Collection<Configurable> {
        val configurable = when (category) {
            DebuggerSettingsCategory.DATA_VIEWS -> createDataViewConfigurable()
            DebuggerSettingsCategory.GENERAL -> createGeneralSettingsConfigurable()
            DebuggerSettingsCategory.STEPPING -> createSteppingConfigurable()
            else -> null
        }
        return listOfNotNull(configurable)
    }

    private fun createDataViewConfigurable(): Configurable {
        return SimpleConfigurable.create(
            DATA_VIEW_ID,
            RsDebuggerBundle.message("settings.rust.debugger.data.view.name"),
            RsDebuggerDataViewConfigurableUi::class.java,
            ::getInstance
        )
    }

    private fun createGeneralSettingsConfigurable(): Configurable {
        return SimpleConfigurable.create(
            GENERAL_SETTINGS_ID,
            RsDebuggerBundle.message("settings.rust.debugger.title"),
            RsDebuggerGeneralSettingsConfigurableUi::class.java,
            Companion::getInstance
        )
    }

    private fun createSteppingConfigurable(): Configurable {
        return SimpleConfigurable.create(
            STEPPING_ID,
            RsDebuggerBundle.message("settings.rust.debugger.title"),
            RsDebuggerSteppingSettingsConfigurableUi::class.java,
            Companion::getInstance
        )
    }

    override fun isTargetedToProduct(configurable: Configurable): Boolean {
        if (configurable !is SearchableConfigurable) return false
        return when (configurable.id) {
            GENERAL_SETTINGS_ID, DATA_VIEW_ID, STEPPING_ID -> true
            else -> false
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): RsDebuggerSettings = getInstance(RsDebuggerSettings::class.java)

        const val GENERAL_SETTINGS_ID: String = "Debugger.Rust.General"
        const val DATA_VIEW_ID: String = "Debugger.Rust.DataView"
        const val STEPPING_ID: String = "Debugger.Rust.Stepping"
    }
}

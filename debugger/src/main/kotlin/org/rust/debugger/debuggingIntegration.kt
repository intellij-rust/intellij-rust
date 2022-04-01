/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import org.rust.ide.debugger.nativeDebuggingSupportPlugin

private val CIDR_DEBUGGER_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.cidr.debugger")

private fun cidrDebuggerPlugin(): IdeaPluginDescriptor? =
    PluginManagerCore.getPlugin(CIDR_DEBUGGER_PLUGIN_ID)

// BACKCOMPAT: 2021.3. Since 2022.1 Rider for Unreal Engine is merged into Rider
//  so `isDebuggingIntegrationEnabled` is always true
fun isDebuggingIntegrationEnabled(): Boolean {
    return cidrDebuggerPlugin() != null || nativeDebuggingSupportPlugin() != null
}

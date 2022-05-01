/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.debugger

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

val NATIVE_DEBUGGING_SUPPORT_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.nativeDebug")

fun nativeDebuggingSupportPlugin(): IdeaPluginDescriptor? =
    PluginManagerCore.getPlugin(NATIVE_DEBUGGING_SUPPORT_PLUGIN_ID)

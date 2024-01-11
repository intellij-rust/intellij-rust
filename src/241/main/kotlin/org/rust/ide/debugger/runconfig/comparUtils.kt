/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.debugger.runconfig

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore

fun PluginManagerCore.getLoadedPlugins(): List<IdeaPluginDescriptor> = loadedPlugins

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser

fun installPluginsAndEnable(
    project: Project?,
    pluginIds: Set<PluginId>,
    showDialog: Boolean = false,
    onSuccess: Runnable
) {
    PluginsAdvertiser.installAndEnable(project, pluginIds, showDialog, onSuccess)
}

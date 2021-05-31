/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable

fun installPluginsAndEnable(
    project: Project?,
    pluginIds: Set<PluginId>,
    showDialog: Boolean = false,
    onSuccess: Runnable
) {
    installAndEnable(project, pluginIds, showDialog, onSuccess)
}

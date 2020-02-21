/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.application.options.ModuleAwareProjectConfigurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager

class RsActiveSdkModuleConfigurable(project: Project)
    : ModuleAwareProjectConfigurable<*>(project, "Project Toolchain", "reference.settings.project.toolchain") {

    override fun createModuleConfigurable(module: Module): UnnamedConfigurable = RsActiveSdkConfigurable(module)

    override fun createDefaultProjectConfigurable(): UnnamedConfigurable = RsActiveSdkConfigurable(project)

    @Throws(ConfigurationException::class)
    override fun apply() {
        super.apply()

        // TODO[catherine] proper per-module caching of framework installed state
        for (module in ModuleManager.getInstance(project).modules) {
            val sdk = ModuleRootManager.getInstance(module).sdk
            if (sdk != null) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    VFSTestFrameworkListener.getInstance().updateAllTestFrameworks(sdk)
                }
                break
            }
        }
    }
}

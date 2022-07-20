/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber

class CargoBuildToolConfigurableProvider(private val project: Project) : ConfigurableProvider() {

    override fun canCreateConfigurable(): Boolean {
        return ApplicationInfo.getInstance().build >= BUILD_222 && CargoConfigurable.buildToolsConfigurableExists(project)
    }

    override fun createConfigurable(): Configurable = CargoConfigurable(project, isPlaceholder = false)

    companion object {
        // BACKCOMPAT: 2022.1
        private val BUILD_222: BuildNumber = BuildNumber.fromString("222")!!
    }
}

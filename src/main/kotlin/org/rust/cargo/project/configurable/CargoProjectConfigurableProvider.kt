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

class CargoProjectConfigurableProvider(private val project: Project) : ConfigurableProvider() {

    override fun createConfigurable(): Configurable {
        val isPlaceholder = ApplicationInfo.getInstance().build >= BUILD_222 && CargoConfigurable.buildToolsConfigurableExists(project)
        return CargoConfigurable(project, isPlaceholder)
    }

    companion object {
        // BACKCOMPAT: 2022.1
        private val BUILD_222: BuildNumber = BuildNumber.fromString("222")!!
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project

class CargoProjectConfigurableProvider(private val project: Project) : ConfigurableProvider() {

    override fun createConfigurable(): Configurable {
        val isPlaceholder = CargoConfigurable.buildToolsConfigurableExists(project)
        return CargoConfigurable(project, isPlaceholder)
    }
}

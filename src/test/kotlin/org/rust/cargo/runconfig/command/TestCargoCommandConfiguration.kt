/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project

/**
 * Checks that `CargoCommandConfiguration` is open
 * not to break other plugins that has child classes like [EduTools](https://plugins.jetbrains.com/plugin/10081-edutools) plugin
 */
@Suppress("unused")
class TestCargoCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : CargoCommandConfiguration(project, name, factory)

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.rust.openapiext.modules

/**
 * Extracts modules with `Cargo.toml` present at the root.
 */
val Project.modulesWithCargoProject: Collection<Module>
    get() = modules.filter { it.cargoProjectRoot != null }

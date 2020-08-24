/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.text.SemVer

/**
 * Service that provides lookup of crate information.
 */
interface CrateResolverService {
    fun isAvailable(): Boolean = true

    /**
     * Returns crates that include `name` in their name.
     */
    fun searchCrates(name: String): Collection<String>

    fun getCrate(name: String): Crate?
}

data class Crate(val name: String, val maxVersion: SemVer?, val versions: List<CrateVersion>)
data class CrateVersion(val version: SemVer?, val yanked: Boolean)

val Project.crateResolver: CrateResolverService get() = service()

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.impl

import com.intellij.openapi.project.Project
import org.rust.toml.crates.Crate
import org.rust.toml.crates.CrateDescription
import org.rust.toml.crates.CrateResolverService

class TestCrateResolverImpl(private val project: Project) : CrateResolverService {
    var testCrates: List<Crate> = listOf()

    override fun searchCrates(name: String): Collection<CrateDescription> = testCrates.filter { name in it.name }.map {
        CrateDescription(it.name, it.maxVersion)
    }

    override fun getCrate(name: String): Crate? = testCrates.firstOrNull { it.name == name }
}

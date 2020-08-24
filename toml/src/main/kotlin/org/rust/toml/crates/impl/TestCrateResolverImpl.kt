/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.impl

import org.rust.toml.crates.Crate
import org.rust.toml.crates.CrateResolverService

class TestCrateResolverImpl : CrateResolverService {
    var testCrates: List<Crate> = listOf()

    override fun searchCrates(name: String): Collection<String> = testCrates.filter { name in it.name }.map {
        it.name
    }

    override fun getCrate(name: String): Crate? = testCrates.firstOrNull { it.name == name }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.rust.toml.crates.*

abstract class CargoTomlTestBase: BasePlatformTestCase() {
    protected fun createCrateResolver(crates: List<Crate>): CrateResolver {
        return object : CrateResolver {
            override fun searchCrate(name: String): Collection<CrateDescription> =
                crates.filter { it.name.contains(name) }.map { CrateDescription(it.name, it.maxVersion) }

            override fun getCrate(name: String): Crate? = crates.find { it.name == name }
        }
    }

    protected fun crate(name: String, maxVersion: String): Crate {
        val semver = parseSemver(maxVersion)!!
        return Crate(name, semver, listOf(CrateVersion(semver, false)))
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import org.jetbrains.annotations.TestOnly

class TestCratesLocalIndexServiceImpl : CratesLocalIndexService {
    var testCrates: Map<String, CargoRegistryCrate> = emptyMap()

    override fun getCrate(crateName: String): CargoRegistryCrate? = testCrates[crateName]
    override fun getAllCrateNames(): List<String> = testCrates.keys.toList()
    override fun updateIfNeeded() {}
}

@TestOnly
fun withMockedCrates(crates: Map<String, CargoRegistryCrate>, action: () -> Unit) {
    val resolver = CratesLocalIndexService.getInstance() as TestCratesLocalIndexServiceImpl
    val orgCrates = resolver.testCrates
    try {
        resolver.testCrates = crates
        action()
    } finally {
        resolver.testCrates = orgCrates
    }
}

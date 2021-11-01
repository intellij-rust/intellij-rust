/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import org.jetbrains.annotations.TestOnly
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Ok

class TestCratesLocalIndexServiceImpl : CratesLocalIndexService {
    var testCrates: Map<String, CargoRegistryCrate> = emptyMap()

    override fun getCrate(crateName: String): RsResult<CargoRegistryCrate?, CratesLocalIndexService.Error> =
        Ok(testCrates[crateName])

    override fun getAllCrateNames(): RsResult<List<String>, CratesLocalIndexService.Error> =
        Ok(testCrates.keys.toList())
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

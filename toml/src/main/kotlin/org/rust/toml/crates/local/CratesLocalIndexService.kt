/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.openapi.components.service
import org.jetbrains.annotations.TestOnly

interface CratesLocalIndexService {
    fun getCrate(crateName: String): CargoRegistryCrate?
    fun getAllCrateNames(): List<String>

    companion object {
        fun getInstance(): CratesLocalIndexService = service()
    }
}

data class CargoRegistryCrate(val versions: List<CargoRegistryCrateVersion>) {
    companion object {
        @TestOnly
        fun of(vararg versions: String): CargoRegistryCrate =
            CargoRegistryCrate(versions.map {
                CargoRegistryCrateVersion(it, false, emptyList())
            })
    }
}

data class CargoRegistryCrateVersion(val version: String, val isYanked: Boolean, val features: List<String>)

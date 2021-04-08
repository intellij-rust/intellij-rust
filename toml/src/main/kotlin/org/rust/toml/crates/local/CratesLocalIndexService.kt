/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.openapi.components.service
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import org.jetbrains.annotations.TestOnly

interface CratesLocalIndexService {
    /**
     * @throws CratesLocalIndexException if index is either being updated, or PersistentHashMap has failed to
     * initialize and not available.
     * @return [CargoRegistryCrate] if there is a crate with such [crateName], and `null` if it is not.
     */
    @Throws(CratesLocalIndexException::class)
    fun getCrate(crateName: String): CargoRegistryCrate?

    /**
     * @throws CratesLocalIndexException if index is either being updated, or PersistentHashMap has failed to
     * initialize and not available.
     * @return list of crate names in the index.
     */
    @Throws(CratesLocalIndexException::class)
    fun getAllCrateNames(): List<String>
    fun updateIfNeeded()

    companion object {
        fun getInstance(): CratesLocalIndexService = service()
    }
}

class CratesLocalIndexException(message: String) : Exception(message)

data class CargoRegistryCrate(val versions: List<CargoRegistryCrateVersion>) {
    val sortedVersions: List<CargoRegistryCrateVersion>
        get() = versions.sortedBy { it.semanticVersion }

    companion object {
        @TestOnly
        fun of(vararg versions: String): CargoRegistryCrate =
            CargoRegistryCrate(versions.map {
                CargoRegistryCrateVersion(it, false, emptyList())
            })
    }
}

data class CargoRegistryCrateVersion(val version: String, val isYanked: Boolean, val features: List<String>) {
    val semanticVersion: Semver?
        get() = try {
            Semver(version, Semver.SemverType.NPM)
        } catch (e: SemverException) {
            null
        }
}

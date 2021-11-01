/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import org.jetbrains.annotations.TestOnly
import org.rust.stdext.RsResult
import java.nio.file.Path

interface CratesLocalIndexService {
    /**
     * @return [CargoRegistryCrate] if there is a crate with such [crateName], and `null` if it is not.
     */
    fun getCrate(crateName: String): RsResult<CargoRegistryCrate?, Error>

    /**
     * @return list of crate names in the index.
     */
    fun getAllCrateNames(): RsResult<List<String>, Error>

    sealed class Error {
        object Updating : Error() {
            override fun toString(): String =
                "CratesLocalIndexService.Error.Updating(Index is being updated)"
        }

        object NotYetLoaded : Error() {
            override fun toString(): String =
                "CratesLocalIndexService.Error.NotYetLoaded(The index is not yet loaded)"
        }

        object Disposed : Error() {
            override fun toString(): String =
                "CratesLocalIndexService.Error.Disposed(The service has been disposed)"
        }

        sealed class InternalError : Error() {
            data class NoCargoIndex(val path: Path) : InternalError()
            data class RepoReadError(val path: Path, val message: String) : InternalError()
            data class PersistentHashMapInitError(val path: Path, val message: String) : InternalError()
            data class PersistentHashMapWriteError(val message: String) : InternalError()
            data class PersistentHashMapReadError(val message: String) : InternalError()
        }
    }

    companion object {
        fun getInstance(): CratesLocalIndexService = service()
        fun getInstanceIfCreated(): CratesLocalIndexService? = serviceIfCreated()
    }
}

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

data class CargoRegistryCrateVersion(
    val version: String,
    val isYanked: Boolean,
    val features: List<String>
) {
    val semanticVersion: Semver?
        get() = try {
            Semver(version, Semver.SemverType.NPM)
        } catch (e: SemverException) {
            null
        }
}

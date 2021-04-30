/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import java.nio.file.Path

// BACKCOMPAT: 2020.3
@Deprecated("Use org.rust.cargo.toolchain.RsToolchain")
@Suppress("DEPRECATION")
class RustToolchain(location: Path) : RsLocalToolchain(location) {

    @Suppress("unused")
    fun rawCargo(): Cargo = Cargo(this)

    companion object {
        fun suggest(): RustToolchain? = RsToolchainBase.suggest()?.let(::from)

        fun from(newToolchain: RsToolchainBase): RustToolchain = RustToolchain(newToolchain.location)
    }
}

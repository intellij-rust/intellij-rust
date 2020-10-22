/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import org.rust.ide.sdk.RsSdkUtils.findOrCreateSdk
import org.rust.ide.sdk.toolchain
import java.nio.file.Path

// BACKCOMPAT: 2020.2
@Deprecated("Use org.rust.cargo.toolchain.RsToolchain")
@Suppress("DEPRECATION")
class RustToolchain(location: Path) : RsLocalToolchain(location, null) {

    fun rawCargo(): Cargo = Cargo(this)

    companion object {
        fun suggest(): RustToolchain? = findOrCreateSdk()?.toolchain?.let(::from)

        fun from(newToolchain: RsToolchain): RustToolchain = RustToolchain(newToolchain.location)
    }
}

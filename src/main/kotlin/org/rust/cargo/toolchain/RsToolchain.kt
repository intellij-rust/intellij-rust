/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import java.nio.file.Path

// BACKCOMPAT: 2021.1
@Deprecated("Use org.rust.cargo.toolchain.RsLocalToolchain")
@Suppress("DEPRECATION")
class RsToolchain(location: Path) : RsLocalToolchain(location) {
    companion object {
        fun suggest(): RsToolchain? = RsToolchainBase.suggest()?.let(::from)

        private fun from(newToolchain: RsToolchainBase): RsToolchain? =
            if (newToolchain is RsLocalToolchain) RsToolchain(newToolchain.location) else null
    }
}

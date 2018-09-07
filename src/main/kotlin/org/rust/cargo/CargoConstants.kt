/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

object CargoConstants {
    const val MANIFEST_FILE: String = "Cargo.toml"
    const val XARGO_MANIFEST_FILE: String = "Xargo.toml"
    const val LOCK_FILE: String = "Cargo.lock"
    const val BUILD_RS_FILE: String = "build.rs"

    const val RUST_BACTRACE_ENV_VAR: String = "RUST_BACKTRACE"

    object ProjectLayout {
        val sources: List<String> = listOf("src", "examples")
        val tests: List<String> = listOf("tests", "benches")
        const val target: String = "target"
    }
}

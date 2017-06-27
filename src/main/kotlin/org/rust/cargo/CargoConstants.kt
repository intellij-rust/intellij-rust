/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

object CargoConstants {

    const val MANIFEST_FILE = "Cargo.toml"
    const val LOCK_FILE = "Cargo.lock"
    const val BUILD_RS_FILE = "build.rs"

    const val RUSTC_ENV_VAR = "RUSTC"
    const val RUST_BACTRACE_ENV_VAR = "RUST_BACKTRACE"

    object Commands {
        val RUN = "run"
        val TEST = "test"
    }

    object ProjectLayout {
        val binaries = listOf("src/bin")
        val sources = listOf("src", "examples")
        val tests = listOf("tests", "benches")
        val target = "target"
    }
}

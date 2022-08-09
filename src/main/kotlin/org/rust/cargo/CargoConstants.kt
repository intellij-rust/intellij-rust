/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

object CargoConstants {

    const val MANIFEST_FILE = "Cargo.toml"
    const val XARGO_MANIFEST_FILE = "Xargo.toml"
    const val LOCK_FILE = "Cargo.lock"

    // From https://doc.rust-lang.org/cargo/reference/config.html#hierarchical-structure:
    //  Cargo also reads config files without the .toml extension, such as .cargo/config.
    //  Support for the .toml extension was added in version 1.39 and is the preferred form.
    //  If both files exist, Cargo will use the file without the extension.
    const val CONFIG_FILE = "config"
    const val CONFIG_TOML_FILE = "config.toml"

    // https://rust-lang.github.io/rustup/overrides.html#the-toolchain-file
    const val TOOLCHAIN_FILE = "rust-toolchain"
    const val TOOLCHAIN_TOML_FILE = "rust-toolchain.toml"

    const val BUILD_FILE = "build.rs"

    const val RUST_BACKTRACE_ENV_VAR = "RUST_BACKTRACE"

    object ProjectLayout {
        val sources = listOf("src", "examples")
        val tests = listOf("tests", "benches")
        const val target = "target"
    }
}

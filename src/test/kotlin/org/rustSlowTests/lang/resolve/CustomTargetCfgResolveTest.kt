/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.lang.resolve

import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.lang.core.psi.RsPath

class CustomTargetCfgResolveTest : RsWithToolchainTestBase() {

    @MinRustcVersion("1.52.0")
    fun `test custom build-in compiler target`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir(".cargo") {
                toml("config", """
                    [build]
                    target = "wasm32-unknown-unknown"
                """)
            }
            dir("src") {
                rust("main.rs", """
                    #[cfg(not(target_arch = "wasm32"))]
                    mod disabled;
                    #[cfg(target_arch = "wasm32")]
                    mod enabled;

                    #[cfg(not(target_arch = "wasm32"))]
                    pub use disabled::function_under_cfg;
                    #[cfg(target_arch = "wasm32")]
                    pub use enabled::function_under_cfg;

                    fn main() {
                        function_under_cfg();
                            //^
                    }
                """)
                rust("disabled.rs", """
                    pub fn function_under_cfg() {}
                """)
                rust("enabled.rs", """
                    pub fn function_under_cfg() {}
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/enabled.rs")
    }

    @MinRustcVersion("1.52.0")
    fun `test custom json target`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir(".cargo") {
                toml("config", """
                    [build]
                    target = "custom-target.json"
                """)
            }
            file("custom-target.json", """
                {
                    "llvm-target": "aarch64-unknown-none",
                    "data-layout": "e-m:e-i64:64-f80:128-n8:16:32:64-S128",
                    "arch": "aarch64",
                    "target-endian": "little",
                    "target-pointer-width": "64",
                    "target-c-int-width": "32",
                    "os": "none",
                    "executables": true,
                    "linker-flavor": "ld.lld",
                    "linker": "rust-lld",
                    "panic-strategy": "abort",
                    "disable-redzone": true,
                    "features": "-mmx,-sse,+soft-float"
                }
            """)
            dir("src") {
                rust("main.rs", """
                    #[cfg(not(target_arch = "aarch64"))]
                    mod disabled;
                    #[cfg(target_arch = "aarch64")]
                    mod enabled;

                    #[cfg(not(target_arch = "aarch64"))]
                    pub use disabled::function_under_cfg;
                    #[cfg(target_arch = "aarch64")]
                    pub use enabled::function_under_cfg;

                    fn main() {
                        function_under_cfg();
                            //^
                    }
                """)
                rust("disabled.rs", """
                    pub fn function_under_cfg() {}
                """)
                rust("enabled.rs", """
                    pub fn function_under_cfg() {}
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/enabled.rs")
    }
}

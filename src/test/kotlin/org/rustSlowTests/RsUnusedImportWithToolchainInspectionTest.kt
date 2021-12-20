/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import org.junit.ComparisonFailure
import org.rust.ExpandMacros
import org.rust.MinRustcVersion
import org.rust.WithExperimentalFeatures
import org.rust.expect
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.ide.inspections.RsWithToolchainInspectionTestBase
import org.rust.ide.inspections.lints.RsUnusedImportInspection
import org.rust.lang.core.macros.MacroExpansionScope

@MinRustcVersion("1.46.0")
class RsUnusedImportWithToolchainInspectionTest : RsWithToolchainInspectionTestBase<Unit>(RsUnusedImportInspection::class) {

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
    fun `test unused imports, usages in serde attribute (current mod)`() = check {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
            serde = { version = "1.0", features = ["derive"] }
            serde_json = "1.0"
        """)

        dir("src") {
            rust("lib.rs", """
                mod inner {/*caret*/
                    pub fn func() -> i32 { 1 }
                }
                use inner::func;

                #[derive(serde::Deserialize)]
                struct Foo {
                    #[serde(default = "func")]
                    field: i32,
                }
            """)
        }
    }

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
    fun `test unused imports, usages in serde attribute (other mod)`() = expect<ComparisonFailure> {
        check {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
                edition = "2018"

                [dependencies]
                serde = { version = "1.0", features = ["derive"] }
                serde_json = "1.0"
            """)

            dir("src") {
                rust("lib.rs", """
                    mod mod1 {/*caret*/
                        pub fn func() -> i32 { 1 }
                    }
                    use mod1::func;

                    mod mod2 {
                        #[derive(serde::Deserialize)]
                        struct Foo {
                            #[serde(default = "super::func")]
                            field: i32,
                        }
                    }
                """)
            }
        }
    }
}

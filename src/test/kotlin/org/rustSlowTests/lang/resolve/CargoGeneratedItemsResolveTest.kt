/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.lang.resolve

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTree
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.RsPath
import org.rust.openapiext.runWithEnabledFeature

class CargoGeneratedItemsResolveTest : RsWithToolchainTestBase() {

    private val tempDirFixture = TempDirTestFixtureImpl()

    override fun setUp() {
        super.setUp()
        tempDirFixture.setUp()
    }

    override fun tearDown() {
        tempDirFixture.tearDown()
        super.tearDown()
    }

    fun `test generated cfg option`() = runWithEnabledFeature(RsExperiments.EVALUATE_BUILD_SCRIPTS) {
        val libraryDir = tempDirFixture.getFile(".")!!
        val library = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(not(has_generated_feature))]
                    mod disabled;
                    #[cfg(has_generated_feature)]
                    mod enabled;

                    #[cfg(not(has_generated_feature))]
                    pub use disabled::function_under_cfg;
                    #[cfg(has_generated_feature)]
                    pub use enabled::function_under_cfg;
                """)
                rust("disabled.rs", """
                    pub fn function_under_cfg() {
                        println!("'has_generated_feature' is disabled")
                    }
                """)
                rust("enabled.rs", """
                    pub fn function_under_cfg() {
                        println!("'has_generated_feature' is enabled")
                    }
                """)
            }

            rust("build.rs", """
                fn main() {
                    println!("cargo:rustc-cfg=has_generated_feature");
                }
            """)
        }.create(project, libraryDir)

        val libraryPath = FileUtil.toSystemIndependentName(library.root.path)

        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                edition = "2018"
                authors = []

                [dependencies]
                foo = { path = "$libraryPath" }
            """)

            dir("src") {
                rust("main.rs", """
                    use foo::function_under_cfg;
                    fn main() {
                        function_under_cfg();
                              //^
                    }
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/main.rs", toFile = ".../src/enabled.rs")
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.intellij.lang.annotations.Language
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.ide.lineMarkers.LineMarkerTestHelper

class CargoFeatureLineMarkerProviderTest : RsWithToolchainTestBase() {

    private lateinit var lineMarkerTestHelper: LineMarkerTestHelper

    override fun setUp() {
        super.setUp()
        lineMarkerTestHelper = LineMarkerTestHelper(myFixture)
    }

    fun `test simple features`() = doTest("""
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [features]
        foo = []   # - Toggle feature `foo`
        bar = []   # - Toggle feature `bar`
        foobar = ["foo", "bar"]  # - Toggle feature `foobar`
    """)

    fun `test optional dependency`() = doTest("""
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [dependencies.foo] # - Toggle feature `foo`
        path = "foo"
        optional = true
    """)

    fun `test optional dependency inline table`() = doTest("""
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [dependencies]
        foo = { path = "foo", optional = true } # - Toggle feature `foo`
    """)

    private fun doTest(@Language("Toml") source: String) {
        val testProject = buildProject {
            toml("Cargo.toml", source)
            dir("src") {
                rust("lib.rs", "")
            }
            dir("foo") {
                toml("Cargo.toml", """
                    [package]
                    name = "foo"
                    version = "0.1.0"
                    authors = []
                """)
                dir("src") {
                    rust("lib.rs", "")
                }
            }
        }

        lineMarkerTestHelper.doTestFromFile(testProject.file("Cargo.toml"))
    }
}

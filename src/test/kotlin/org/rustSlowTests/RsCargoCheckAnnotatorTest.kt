/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.lang.annotation.HighlightSeverity
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.fileTree

class RsCargoCheckAnnotatorTest : RustWithToolchainTestBase() {
    override val dataPath = "src/test/resources/org/rust/cargo/check/fixtures"

    fun `test no errors if everything is ok`() = doTest("""
        fn main() { println!("Hello, World!"); }
    """)

    fun `test highlights type errors`() = doTest("""
        struct X; struct Y;
        fn main() {
            let _: X = <error>Y</error>;
        }
    """)

    fun `test fixes up unused function span`() = doTest("""
        fn <weak_warning>foo</weak_warning>() {
            let _ = 46 * 2;
        }

        fn main() {}
    """)

    fun `test highlights from other files do not interfer`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", "mod foo; fn main() {}")
                rust("foo.rs", """
                    struct X; struct Y;
                    fn foo() {
                        let _: X = Y;
                    }
                """)
            }
        }.create()
        myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!)
        val highlights = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)
        check(highlights.isEmpty(), {
            "Did not expect any highlights, got:\n$highlights"
        })
    }

    fun `test don't report syntax errors from cargo check`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() { bla bla bla }
                    fn foo() { let : X = () }
                """)
            }
        }.create()
        myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!)
        val highlights = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)
        val descriptions = highlights.map { it.description }.joinToString(separator = "\n")
        val expected = """
            cannot find value `bla` in this scope
            '!', '&', '(', '::', ';', '[', '^', '{', '|' or '}' expected, got 'bla'
            '!', '&', '(', '::', ';', '[', '^', '{', '|' or '}' expected, got 'bla'
            <pat> expected, got ':'
        """.trimIndent()
        check(expected == descriptions) {
            "Expected:\n$expected\nGot:\n$descriptions"
        }
    }

    private fun doTest(mainRs: String) {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                file("main.rs", mainRs)
            }
        }.create()
        myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!)
        myFixture.checkHighlighting()
    }
}

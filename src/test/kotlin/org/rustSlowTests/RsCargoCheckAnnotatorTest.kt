/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.lang.annotation.HighlightSeverity
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.fileTree

class RsCargoCheckAnnotatorTest : RustWithToolchainTestBase() {

    override fun setUp() {
        super.setUp()
        project.rustSettings.data = project.rustSettings.data.copy(useCargoCheckAnnotator = true)
    }

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

    // https://github.com/intellij-rust/intellij-rust/issues/1497
    fun `test don't try to highlight non project files`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []

                [dependencies]
                rand = "0.3.15"
            """)

            dir("src") {
                rust("lib.rs", """
                    extern crate rand;

                    fn foo() {
                        let a: i32 = "
                        ";
                    }
                """)
            }
        }.create()

        val path = myModule.cargoWorkspace
            ?.findPackage("rand")
            ?.contentRoot
            ?.findFileByRelativePath("src/lib.rs")
            ?: error("Can't find 'src/lib.rs' in 'rand' library")
        myFixture.openFileInEditor(path)

        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        check(highlights.isEmpty(), {
            "Did not expect any highlights, got:\n$highlights"
        })
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

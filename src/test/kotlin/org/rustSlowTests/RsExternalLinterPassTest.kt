/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.lang.annotation.HighlightSeverity
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.ide.annotator.RsExternalLinterUtils

class RsExternalLinterPassTest : RsWithToolchainTestBase() {

    override fun setUp() {
        super.setUp()
        project.rustSettings.modifyTemporary(testRootDisposable) { it.runExternalLinterOnTheFly = true }
    }

    fun `test no errors if everything is ok`() = doTest("""
        fn main() { println!("Hello, World!"); }
    """)

    fun `test highlights type errors`() = doTest("""
        struct X; struct Y;
        fn main() {
            let _: X = <error descr="${RsExternalLinterUtils.TEST_MESSAGE}">Y</error>;
        }
    """)

    @MinRustcVersion("1.57.0")
    fun `test highlights errors from macro`() = doTest("""
        fn main() {
            let mut x = 42;
            let r = &mut x;
            <error descr="${RsExternalLinterUtils.TEST_MESSAGE}">dbg!(x)</error>;
            dbg!(r);
        }
    """)

    fun `test highlights errors in tests`() = doTest("""
        fn main() {}

        #[test]
        fn test() {
            let x: i32 = <error descr="${RsExternalLinterUtils.TEST_MESSAGE}">0.0</error>;
        }
    """)

    fun `test highlights clippy errors`() = doTest("""
        fn main() {
            <weak_warning descr="${RsExternalLinterUtils.TEST_MESSAGE}">if true { true } else { false }</weak_warning>;
        }
    """, externalLinter = ExternalLinter.CLIPPY)

    fun `test workspace features`() = doTest("""
        fn main() {}

        #[cfg(feature = "enabled_feature")]
        fn foo() {
            let x: i32 = <error descr="${RsExternalLinterUtils.TEST_MESSAGE}">0.0</error>;
        }

        #[cfg(feature = "disabled_feature")]
        fn foo() {
            let x: i32 = 0.0;
        }
    """)

    fun `test highlights from other files do not interfere`() {
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
        check(highlights.isEmpty()) {
            "Did not expect any highlights, got:\n$highlights"
        }
    }

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

        val path = project.cargoProjects.singleWorkspace()
            .findPackageByName("rand")
            ?.contentRoot
            ?.findFileByRelativePath("src/lib.rs")
            ?: error("Can't find 'src/lib.rs' in 'rand' library")
        myFixture.openFileInEditor(path)

        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        check(highlights.isEmpty()) {
            "Did not expect any highlights, got:\n$highlights"
        }
    }

    // https://github.com/intellij-rust/intellij-rust/issues/2503
    fun `test unique errors`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                file("main.rs", """
                    fn main() {
                        for x in "xs" {}
                    }
                """)
            }
        }.create()
        myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!)
        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
            .filter { it.description == RsExternalLinterUtils.TEST_MESSAGE }
        check(highlights.size == 1) {
            "Expected only one error highlights from `RsExternalLinterPass`, got:\n$highlights"
        }
    }

    fun `test add reference to index for compiler errors from index`() = checkTooltip("""
        fn main() {
            let _: () = 0;
        }
    """, """
        mismatched types <a href="<a href='https://doc.rust-lang.org/error-index.html#E0308'>https://doc.rust-lang.org/error-index.html#E0308</a>">E0308</a><br>expected `()`, found integer
    """)

    @MinRustcVersion("1.44.1")
    fun `test don't add reference to index for compiler errors not from index`() = checkTooltip("""
        fn main() {
            let x = ();
        }
    """, """
        unused variable: `x`
        Note: `#[warn(unused_variables)]` on by default
        Help: if this is intentional, prefix it with an underscore
    """)

    @MinRustcVersion("1.45.0")
    fun `test handle hyperlinks in errors`() = checkTooltip("""
        #![deny(clippy::unwrap_used)]
        fn main() {
            let x = Some(());
            x.unwrap()
        }
    """, """
        used `unwrap()` on `an Option` value
        Note: the lint level is defined here
        Help: if you don't want to handle the `None` case gracefully, consider using `expect()` to provide a better panic message
        Help: for further information visit <a href='https://rust-lang.github.io/rust-clippy/master/index.html#unwrap_used'>https://rust-lang.github.io/rust-clippy/master/index.html#unwrap_used</a>
    """, externalLinter = ExternalLinter.CLIPPY)

    private fun doTest(
        @Language("Rust") mainRs: String,
        externalLinter: ExternalLinter = ExternalLinter.DEFAULT
    ) {
        project.rustSettings.modifyTemporary(testRootDisposable) { it.externalLinter = externalLinter }
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []

                [features]
                disabled_feature = []
                enabled_feature = []
                enabled2_feature = []
            """)

            dir("src") {
                file("main.rs", mainRs)
            }
        }.create()
        val cargoProject = project.cargoProjects.singleProject()
        val pkg = cargoProject.workspaceOrFail().packages.single { it.origin == PackageOrigin.WORKSPACE }
        project.cargoProjects.modifyFeatures(cargoProject, setOf(PackageFeature(pkg, "disabled_feature")), FeatureState.Disabled)
        myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!)
        myFixture.checkHighlighting()
    }

    // BACKCOMPAT: 2021.1. Use `tooltip` attribute instead
    private fun checkTooltip(
        @Language("Rust") mainRs: String,
        tooltip: String,
        externalLinter: ExternalLinter = ExternalLinter.DEFAULT
    ) {
        project.rustSettings.modifyTemporary(testRootDisposable) { it.externalLinter = externalLinter }
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
        val highlight = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)
            .single { it.description == RsExternalLinterUtils.TEST_MESSAGE }
        assertEquals(tooltip.trimIndent().replace("\n", "<br>"), highlight.toolTip)
    }
}

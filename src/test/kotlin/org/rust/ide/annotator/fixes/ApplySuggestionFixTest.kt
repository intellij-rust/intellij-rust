/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInsight.intention.IntentionAction
import org.intellij.lang.annotations.Language
import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.fileTree
import org.rust.replaceCaretMarker

class ApplySuggestionFixTest : RsWithToolchainTestBase() {

    override fun setUp() {
        super.setUp()
        project.rustSettings.modifyTemporary(testRootDisposable) { it.runExternalLinterOnTheFly = true }
    }

    fun `test rustc suggestion (machine applicable)`() = checkFixByText("""
        pub fn main() {
            let <weak_warning>x</weak_warning> = 0;
        }
    """, """
        pub fn main() {
            let _x = 0;
        }
    """)

    fun `test rustc suggestion (maybe incorrect)`() = checkFixByText("""
        struct Foo(i32);

        impl Foo {
            fn foo(self) -> i32 {
                <error>this</error>.0
            }
        }
    """, """
        struct Foo(i32);

        impl Foo {
            fn foo(self) -> i32 {
                self.0
            }
        }
    """)

    fun `test rustc suggestion (has placeholders)`() = checkFixByText("""
        struct S { x: i32, y: i32 }
        impl S {
            fn new() -> Self { <error>Self</error> }
        }
    """, """
        struct S { x: i32, y: i32 }
        impl S {
            fn new() -> Self { Self { /* fields */ } }
        }
    """)

    fun `test rustc suggestion (unspecified)`() = checkFixByText("""
        fn foo<'a>(x: &i32, y: &'a i32) -> &'a i32 {
            if x > y { <error>x</error> } else { y }
        }
    """, """
        fn foo<'a>(x: &'a i32, y: &'a i32) -> &'a i32 {
            if x > y { x } else { y }
        }
    """)

    fun `test clippy suggestion`() = checkFixByText("""
        pub fn main() {
            <weak_warning>if true { true } else { false }</weak_warning>;
        }
    """, """
        pub fn main() {
            true;
        }
    """, externalLinter = ExternalLinter.CLIPPY)

    @MinRustcVersion("1.50.0")
    fun `test multi-fix suggestion`() = checkFixIsUnavailable("""
        #[deny(clippy::unnecessary_wraps)]
        <error>fn foo() -> Option<i32> { Some(1) }</error>
    """, externalLinter = ExternalLinter.CLIPPY)

    fun `test multi-primary fix suggestion`() = checkFixIsUnavailable("""
        #[deny(clippy::let_and_return)]
        fn _foo() -> i32 {
            let x = 42;
            <error>x</error>
        }
    """, externalLinter = ExternalLinter.CLIPPY)

    fun `test suppress fix`() = checkFixByText("""
        type _SendVec<T: <weak_warning>Send</weak_warning>> = Vec<T>;
    """, """
        #[allow(type_alias_bounds)]
        type _SendVec<T: Send> = Vec<T>;
    """, "Suppress `type_alias_bounds` for type _SendVec")

    private fun checkFixByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        fixName: String? = null,
        externalLinter: ExternalLinter = ExternalLinter.DEFAULT
    ) {
        val action = getQuickFixes(before, fixName, externalLinter).singleOrNull() ?: return // BACKCOMPAT: Rust ???
        myFixture.launchAction(action)
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    private fun checkFixIsUnavailable(
        @Language("Rust") text: String,
        externalLinter: ExternalLinter = ExternalLinter.DEFAULT
    ) {
        assertEmpty(getQuickFixes(text, null, externalLinter))
    }

    private fun getQuickFixes(
        @Language("Rust") text: String,
        fixName: String?,
        externalLinter: ExternalLinter
    ): List<IntentionAction> {
        project.rustSettings.modifyTemporary(testRootDisposable) { it.externalLinter = externalLinter }
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                file("lib.rs", text)
            }
        }.create()
        val filePath = "src/lib.rs"
        myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath(filePath)!!)
        myFixture.checkHighlighting()
        return myFixture.getAllQuickFixes(filePath).filter { fix ->
            if (fixName != null) fix.text == fixName else fix.text.startsWith("External Linter: ")
        }
    }
}

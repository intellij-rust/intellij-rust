/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.commands

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.toolchain.RustChannel
import org.rust.fileTree
import org.rust.ide.formatter.RustfmtTestmarks
import org.rust.launchAction
import org.rust.openapiext.RsProcessExecutionException
import org.rust.openapiext.saveAllDocuments

class RustfmtTest : RsWithToolchainTestBase() {

    fun `test rustfmt is used for whole file formatting`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """)

    fun `test rustfmt is not used for part of file formatting`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn foo() {
                println!("Hello, ΣΠ∫!");
                }

                fn main() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn foo() {
            println!("Hello, ΣΠ∫!");
        }

        fn main() {
        println!("Hello, ΣΠ∫!");
        }
    """) {
        val endOffset = file.text.indexOf("fn main")
        val textRange = TextRange(file.textRange.startOffset, endOffset)
        reformatRange(file, textRange, shouldHitTestmark = false)
    }

    fun `test rustfmt file action`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """) { reformatFile(myFixture.editor) }

    fun `test rustfmt file action ignores emit option 1`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """) {
        project.rustfmtSettings.modifyTemporary(testRootDisposable) {
            it.additionalArguments = "--emit files"
        }
        reformatFile(myFixture.editor)
    }

    fun `test rustfmt file action ignores emit option 2`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """) {
        project.rustfmtSettings.modifyTemporary(testRootDisposable) {
            it.additionalArguments = "--emit=files"
        }
        reformatFile(myFixture.editor)
    }

    fun `test rustfmt file action with edited configuration 1`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """) {
        project.rustfmtSettings.modifyTemporary(testRootDisposable) {
            it.additionalArguments = "--unstable-features"
            it.channel = RustChannel.NIGHTLY
        }
        reformatFile(myFixture.editor)
    }

    fun `test rustfmt file action with edited configuration 2`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
        println!("Hello, ΣΠ∫!");
        }
    """) {
        project.rustfmtSettings.modifyTemporary(testRootDisposable) {
            it.additionalArguments = "--unstable-features"
            it.channel = RustChannel.STABLE
        }
        assertThrows(RsProcessExecutionException::class.java) {
            reformatFile(myFixture.editor)
        }
    }

    fun `test rustfmt file action supports toolchain override`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """) {
        project.rustfmtSettings.modifyTemporary(testRootDisposable) {
            it.additionalArguments = "+nightly --unstable-features"
        }
        reformatFile(myFixture.editor)
    }

    fun `test rustfmt file action edition 2018`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
            edition = "2018"
        """)

        dir("src") {
            rust("main.rs", """
                async fn foo() {/*caret*/
                println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        async fn foo() {
            println!("Hello, ΣΠ∫!");
        }
    """) { reformatFile(myFixture.editor) }

    fun `test rustfmt cargo project action`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                    println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """) { reformatCargoProject() }

    fun `test rustfmt cargo project action with edited configuration 1`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                    println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """) {
        project.rustfmtSettings.modifyTemporary(testRootDisposable) {
            it.additionalArguments = "--unstable-features"
            it.channel = RustChannel.NIGHTLY
        }
        reformatCargoProject()
    }

    fun `test rustfmt cargo project action with edited configuration 2`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                    println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }, """
        fn main() {
            println!("Hello, ΣΠ∫!");
        }
    """) {
        project.rustfmtSettings.modifyTemporary(testRootDisposable) {
            it.additionalArguments = "--unstable-features"
            it.channel = RustChannel.STABLE
        }
        assertThrows(RsProcessExecutionException::class.java) {
            reformatCargoProject()
        }
    }

    fun `test rustfmt on save`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                    println!("Hello, ΣΠ∫!");
                }
            """)
        }
    }) {
        myFixture.type("\n\n\n")
        project.rustfmtSettings.modifyTemporary(testRootDisposable) { it.runRustfmtOnSave = true }
        saveAllDocuments()
    }

    fun `test use config from project root`() = doTest({
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        toml("rustfmt.toml", """
            remove_nested_parens = false # default: true
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {/*caret*/
                ((((foo()))));
                }
            """)
        }
    }, """
        fn main() {
            ((((foo()))));
        }
    """)

    fun `test use config from workspace root (rustfmt dot toml)`() = doTest({
        toml("Cargo.toml", """
            [workspace]
            members = [
                "hello"
            ]
        """)

        dir("hello") {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            toml("rustfmt.toml", """
                remove_nested_parens = false
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {/*caret*/
                    ((((foo()))));
                    }
                """)
            }
        }
    }, """
        fn main() {
            ((((foo()))));
        }
    """)

    fun `test use config from workspace root (dot rustfmt dot toml)`() = doTest({
        toml("Cargo.toml", """
            [workspace]
            members = [
                "hello"
            ]
        """)

        dir("hello") {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            toml(".rustfmt.toml", """
                remove_nested_parens = false
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {/*caret*/
                    ((((foo()))));
                    }
                """)
            }
        }
    }, """
        fn main() {
            ((((foo()))));
        }
    """)

    fun `test use config from workspace root overrides config from project root`() = doTest({
        toml("Cargo.toml", """
            [workspace]
            members = [
                "hello"
            ]
        """)

        toml("rustfmt.toml", """
            control_brace_style = true
        """)

        dir("hello") {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            toml("rustfmt.toml", """
                remove_nested_parens = false
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {/*caret*/
                    ((((foo()))));
                    }
                """)
            }
        }
    }, """
        fn main() {
            ((((foo()))));
        }
    """)

    fun `test use config from project root if config from workspace root is not presented`() = doTest({
        toml("Cargo.toml", """
            [workspace]
            members = [
                "hello"
            ]
        """)

        toml("rustfmt.toml", """
            remove_nested_parens = false
        """)

        dir("hello") {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {/*caret*/
                    ((((foo()))));
                    }
                """)
            }
        }
    }, """
        fn main() {
            ((((foo()))));
        }
    """)

    private fun reformatRange(file: PsiFile, textRange: TextRange = file.textRange, shouldHitTestmark: Boolean = true) {
        project.rustfmtSettings.modifyTemporary(testRootDisposable) { it.useRustfmt = true }
        val testmark = RustfmtTestmarks.RustfmtUsed
        val checkMark: (() -> Unit) -> Unit = if (shouldHitTestmark) testmark::checkHit else testmark::checkNotHit
        checkMark {
            WriteCommandAction.runWriteCommandAction(project, ReformatCodeProcessor.getCommandName(), null, {
                CodeStyleManager.getInstance(project).reformatRange(file, textRange.startOffset, textRange.endOffset)
            })
        }
    }

    private fun reformatFile(editor: Editor) {
        myFixture.launchAction("Cargo.RustfmtFile", CommonDataKeys.EDITOR_EVEN_IF_INACTIVE to editor)
    }

    private fun reformatCargoProject() {
        myFixture.launchAction("Cargo.RustfmtCargoProject")
    }

    /**
     * Note: [expectedTextSupplier] is called right before [action].
     */
    private fun doTest(
        treeBuilder: FileTreeBuilder.() -> Unit,
        expectedTextSupplier: () -> String = { editor.document.text },
        action: () -> Unit = { reformatRange(myFixture.file) }
    ) {
        val fileWithCaret = fileTree(treeBuilder).create().fileWithCaret
        myFixture.configureFromTempProjectFile(fileWithCaret)
        val expected = expectedTextSupplier()
        action()
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(expected.trim(), editor.document.text.trim())
    }

    private fun doTest(
        treeBuilder: FileTreeBuilder.() -> Unit,
        @Language("Rust") expectedText: String,
        action: () -> Unit = { reformatRange(myFixture.file) }
    ) = doTest(treeBuilder, { expectedText.trimIndent() }, action)
}

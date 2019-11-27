/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.commands

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import org.intellij.lang.annotations.Language
import org.rust.FileTreeBuilder
import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.rustSettings
import org.rust.fileTree
import org.rust.ide.actions.RustfmtCargoProjectAction
import org.rust.ide.actions.RustfmtFileAction
import org.rust.openapiext.saveAllDocuments

class RustfmtTest : RsWithToolchainTestBase() {

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
    """)

    @MinRustcVersion("1.31.0")
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
    """)

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
        project.rustSettings.modify { it.runRustfmtOnSave = true }
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
    })

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
    })

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
    })

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
    })

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
    })

    private fun reformatDocument(editor: Editor) {
        val dataContext = MapDataContext(mapOf(
            CommonDataKeys.PROJECT to project,
            CommonDataKeys.EDITOR_EVEN_IF_INACTIVE to editor
        ))
        val action = RustfmtFileAction()
        val e = TestActionEvent(dataContext, action)
        action.beforeActionPerformedUpdate(e)
        check(e.presentation.isEnabledAndVisible) {
            "Failed to run `${RustfmtFileAction::class.java.simpleName}` action"
        }

        action.actionPerformed(e)
    }

    private fun reformatCargoProject() {
        val dataContext = MapDataContext(mapOf(
            CommonDataKeys.PROJECT to project
        ))
        val action = RustfmtCargoProjectAction()
        val e = TestActionEvent(dataContext, action)
        action.beforeActionPerformedUpdate(e)
        check(e.presentation.isEnabledAndVisible) {
            "Failed to run `${RustfmtCargoProjectAction::class.java.simpleName}` action"
        }

        action.actionPerformed(e)
    }

    /**
     * Note: [expectedTextSupplier] is called right before [action].
     */
    private fun doTest(
        treeBuilder: FileTreeBuilder.() -> Unit,
        expectedTextSupplier: () -> String = { editor.document.text },
        action: () -> Unit = { reformatDocument(myFixture.editor) }
    ) {
        val fileWithCaret = fileTree(treeBuilder).create().fileWithCaret
        myFixture.configureFromTempProjectFile(fileWithCaret)
        val expected = expectedTextSupplier()
        action()
        assertEquals(expected.trim(), editor.document.text.trim())
    }

    private fun doTest(
        treeBuilder: FileTreeBuilder.() -> Unit,
        @Language("Rust") expectedText: String,
        action: () -> Unit = { reformatDocument(myFixture.editor) }
    ) = doTest(treeBuilder, { expectedText.trimIndent() }, action)
}

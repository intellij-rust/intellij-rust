/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.commands

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.rustSettings
import org.rust.fileTree
import org.rust.ide.actions.RustfmtCargoProjectAction
import org.rust.ide.actions.RustfmtFileAction
import org.rust.openapiext.document
import org.rust.openapiext.saveAllDocuments

class RustfmtTest : RsWithToolchainTestBase() {

    fun `test rustfmt file action`() {
        val fileWithCaret = fileTree {
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
        }.create().fileWithCaret

        myFixture.configureFromTempProjectFile(fileWithCaret)
        reformatDocument(myFixture.editor)
        assertEquals("""
            fn main() {
                println!("Hello, ΣΠ∫!");
            }
        """.trimIndent(), myFixture.editor.document.text.trim())
    }

    fun `test rustfmt cargo project action`() {
        val fileWithCaret = fileTree {
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
        }.create().fileWithCaret

        myFixture.configureFromTempProjectFile(fileWithCaret)
        reformatCargoProject()
        assertEquals("""
            fn main() {
                println!("Hello, ΣΠ∫!");
            }
        """.trimIndent(), myFixture.editor.document.text.trim())
    }

    fun `test rustfmt on save`() {
        project.rustSettings.modify { it.runRustfmtOnSave = true }

        val fileWithCaret = fileTree {
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
        }.create().fileWithCaret

        val file = myFixture.configureFromTempProjectFile(fileWithCaret).virtualFile
        val document = file.document!!
        val prevText = document.text
        myFixture.type("\n\n\n")

        saveAllDocuments()
        assertEquals(prevText.trim(), document.text.trim())
    }

    fun `test use config from project root`() {
        val fileWithCaret = fileTree {
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
        }.create().fileWithCaret

        myFixture.configureFromTempProjectFile(fileWithCaret)
        val prevText = editor.document.text
        reformatDocument(myFixture.editor)
        assertEquals(prevText.trim(), editor.document.text.trim())
    }

    fun `test use config from workspace root (rustfmt dot toml)`() {
        val fileWithCaret = fileTree {
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
        }.create().fileWithCaret

        myFixture.configureFromTempProjectFile(fileWithCaret)
        val prevText = editor.document.text
        reformatDocument(myFixture.editor)
        assertEquals(prevText.trim(), editor.document.text.trim())
    }

    fun `test use config from workspace root (dot rustfmt dot toml)`() {
        val fileWithCaret = fileTree {
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
        }.create().fileWithCaret

        myFixture.configureFromTempProjectFile(fileWithCaret)
        val prevText = editor.document.text
        reformatDocument(myFixture.editor)
        assertEquals(prevText.trim(), editor.document.text.trim())
    }

    fun `test use config from workspace root overrides config from project root`() {
        val fileWithCaret = fileTree {
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
        }.create().fileWithCaret

        myFixture.configureFromTempProjectFile(fileWithCaret)
        val prevText = editor.document.text
        reformatDocument(myFixture.editor)
        assertEquals(prevText.trim(), editor.document.text.trim())
    }

    fun `test use config from project root if config from workspace root is not presented`() {
        val fileWithCaret = fileTree {
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
        }.create().fileWithCaret

        myFixture.configureFromTempProjectFile(fileWithCaret)
        val prevText = editor.document.text
        reformatDocument(myFixture.editor)
        assertEquals(prevText.trim(), editor.document.text.trim())
    }

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
}

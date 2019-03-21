/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.commands

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.rustSettings
import org.rust.fileTree
import org.rust.ide.actions.RustfmtFileAction
import org.rust.openapiext.document
import org.rust.openapiext.saveAllDocuments

class RustfmtTest : RsWithToolchainTestBase() {

    fun `test rustfmt action`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                    println!("Hello, ΣΠ∫!");
                    }
                """)
            }
        }.create()

        val main = cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!
        reformat(main)
    }

    fun `test save document before rustfmt execution`() {
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

        reformat(file)
        assertEquals(prevText.trim(), document.text.trim())
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
        fileTree {
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
                    fn main() {
                        ((((foo()))));
                    }
                """)
            }
        }.create()

        val file = cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!
        val document = file.document!!
        val prevText = document.text

        reformat(file)
        assertEquals(prevText.trim(), document.text.trim())
    }

    fun `test use config from workspace root (rustfmt dot toml)`() {
        fileTree {
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
                        fn main() {
                            ((((foo()))));
                        }
                    """)
                }
            }
        }.create()

        val file = cargoProjectDirectory.findFileByRelativePath("hello/src/main.rs")!!
        val document = file.document!!
        val prevText = document.text

        reformat(file)
        assertEquals(prevText.trim(), document.text.trim())
    }

    fun `test use config from workspace root (dot rustfmt dot toml)`() {
        fileTree {
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
                        fn main() {
                            ((((foo()))));
                        }
                    """)
                }
            }
        }.create()

        val file = cargoProjectDirectory.findFileByRelativePath("hello/src/main.rs")!!
        val document = file.document!!
        val prevText = document.text

        reformat(file)
        assertEquals(prevText.trim(), document.text.trim())
    }

    fun `test use config from workspace root overrides config from project root`() {
        fileTree {
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
                        fn main() {
                            ((((foo()))));
                        }
                    """)
                }
            }
        }.create()

        val file = cargoProjectDirectory.findFileByRelativePath("hello/src/main.rs")!!
        val document = file.document!!
        val prevText = document.text

        reformat(file)
        assertEquals(prevText.trim(), document.text.trim())
    }

    fun `test use config from project root if config from workspace root is not presented`() {
        fileTree {
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
                        fn main() {
                            ((((foo()))));
                        }
                    """)
                }
            }
        }.create()

        val file = cargoProjectDirectory.findFileByRelativePath("hello/src/main.rs")!!
        val document = file.document!!
        val prevText = document.text

        reformat(file)
        assertEquals(prevText.trim(), document.text.trim())
    }

    private fun reformat(file: VirtualFile) {
        val dataContext = MapDataContext(mapOf(
            CommonDataKeys.PROJECT to project,
            CommonDataKeys.VIRTUAL_FILE to file
        ))
        val action = RustfmtFileAction()
        val e = TestActionEvent(dataContext, action)
        action.beforeActionPerformedUpdate(e)
        check(e.presentation.isEnabledAndVisible) {
            "Failed to run `${RustfmtFileAction::class.java.simpleName}` action"
        }

        action.actionPerformed(e)
    }
}

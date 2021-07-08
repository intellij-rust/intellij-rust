/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import org.rust.FileTree
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTree
import org.rust.lang.core.psi.RsFile
import org.rust.launchAction
import org.rust.openapiext.toPsiFile

class RsPromoteModuleToDirectoryActionTest : RsWithToolchainTestBase() {

    fun `test works on file`() = checkAvailable(
        "foo.rs",
        fileTree {
            rust("foo.rs", "fn hello() {}")
        },
        fileTree {
            dir("foo") {
                rust("mod.rs", "fn hello() {}")
            }
        }
    )

    fun `test not available on mod rs`() = checkNotAvailable(
        "foo/mod.rs",
        fileTree {
            dir("foo") {
                rust("mod.rs", "")
            }
        }
    )

    fun `test not available on library crate root`() = checkNotAvailable("src/lib.rs") {
        dir("src") {
            rust("lib.rs", "")
        }
    }

    fun `test not available on main binary crate root`() = checkNotAvailable("src/main.rs") {
        dir("src") {
            rust("main.rs", "")
        }
    }

    fun `test binary crate root 1`() = checkAvailable("src/bin/binary.rs",
        {
            dir("src") {
                dir("bin") {
                    rust("binary.rs", "")
                }
            }
        },
        {
            dir("src") {
                dir("bin") {
                    dir("binary") {
                        rust("main.rs", "")
                    }
                }
            }
        }
    )

    fun `test binary crate root 2`() = checkNotAvailable("src/bin/binary/main.rs") {
        dir("src") {
            dir("bin") {
                dir("binary") {
                    rust("main.rs", "")
                }
            }
        }
    }

    fun `test bench crate root 1`() = checkAvailable("benches/bench.rs",
        {
            dir("benches") {
                rust("bench.rs", "")
            }
        },
        {
            dir("benches") {
                dir("bench") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test bench crate root 2`() = checkNotAvailable("benches/bench/main.rs") {
        dir("benches") {
            dir("bench") {
                rust("main.rs", "")
            }
        }
    }

    fun `test example crate root 1`() = checkAvailable("examples/example.rs",
        {
            dir("examples") {
                rust("example.rs", "")
            }
        },
        {
            dir("examples") {
                dir("example") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test example crate root 2`() = checkNotAvailable("examples/example/main.rs") {
        dir("examples") {
            dir("example") {
                rust("main.rs", "")
            }
        }
    }

    fun `test test crate root 1`() = checkAvailable("tests/test.rs",
        {
            dir("tests") {
                rust("test.rs", "")
            }
        },
        {
            dir("tests") {
                dir("test") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test test crate root 2`() = checkNotAvailable("tests/test/main.rs") {
        dir("tests") {
            dir("test") {
                rust("main.rs", "")
            }
        }
    }

    private fun checkAvailable(target: String, before: FileTree, after: FileTree) {
        val file = before.create().psiFile(target)
        testActionOnElement(file, shouldBeEnabled = true)
        after.assertEquals(myFixture.findFileInTempDir("."))
    }

    private fun checkNotAvailable(target: String, before: FileTree) {
        val file = before.create().psiFile(target)
        testActionOnElement(file, shouldBeEnabled = false)
    }

    private fun checkAvailable(target: String, before: FileTreeBuilder.() -> Unit, after: FileTreeBuilder.() -> Unit) {
        val file = buildProjectAndFindFile(before, target)
        testActionOnElement(file, shouldBeEnabled = true)
        fileTreeWithCargoToml(after).assertEquals(myFixture.findFileInTempDir("."))
    }

    private fun checkNotAvailable(target: String, before: FileTreeBuilder.() -> Unit) {
        val file = buildProjectAndFindFile(before, target)
        testActionOnElement(file, shouldBeEnabled = false)
    }

    private fun buildProjectAndFindFile(before: FileTreeBuilder.() -> Unit, target: String): RsFile {
        fileTreeWithCargoToml(before).create()
        val file = myFixture.findFileInTempDir(target)!!.toPsiFile(project) as RsFile
        check(file.crate != null)
        return file
    }

    private fun testActionOnElement(element: PsiElement, shouldBeEnabled: Boolean) {
        myFixture.launchAction(
            "Rust.RsPromoteModuleToDirectoryAction",
            CommonDataKeys.PSI_ELEMENT to element,
            shouldBeEnabled = shouldBeEnabled
        )
    }
}

private fun fileTreeWithCargoToml(builder: FileTreeBuilder.() -> Unit): FileTree = fileTree {
    toml(
        "Cargo.toml", """
        [package]
        name = "foo"
        version = "0.1.0"
        authors = []
    """
    )
    builder()
}

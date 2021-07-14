/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import org.rust.FileTree
import org.rust.RsTestBase
import org.rust.fileTree
import org.rust.launchAction

class RsPromoteModuleToDirectoryActionTest : RsTestBase() {

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

    fun `test not available on library crate root`() = checkNotAvailable("lib.rs",
        fileTree {
            rust("lib.rs", "")
        }
    )

    fun `test not available on main binary crate root`() = checkNotAvailable("main.rs",
        fileTree {
            rust("main.rs", "")
        }
    )

    fun `test binary crate root 1`() = checkAvailable("bin/a.rs",
        fileTree {
            dir("bin") {
                rust("a.rs", "")
            }
        },
        fileTree {
            dir("bin") {
                dir("a") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test binary crate root 2`() = checkNotAvailable("bin/a/main.rs",
        fileTree {
            dir("bin") {
                dir("a") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test bench crate root 1`() = checkAvailable("bench/a.rs",
        fileTree {
            dir("bench") {
                rust("a.rs", "")
            }
        },
        fileTree {
            dir("bench") {
                dir("a") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test bench crate root 2`() = checkNotAvailable("bench/a/main.rs",
        fileTree {
            dir("bench") {
                dir("a") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test example crate root 1`() = checkAvailable("example/a.rs",
        fileTree {
            dir("example") {
                rust("a.rs", "")
            }
        },
        fileTree {
            dir("example") {
                dir("a") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test example crate root 2`() = checkNotAvailable("example/a/main.rs",
        fileTree {
            dir("example") {
                dir("a") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test test crate root 1`() = checkAvailable("tests/a.rs",
        fileTree {
            dir("tests") {
                rust("a.rs", "")
            }
        },
        fileTree {
            dir("tests") {
                dir("a") {
                    rust("main.rs", "")
                }
            }
        }
    )

    fun `test test crate root 2`() = checkNotAvailable("tests/a/main.rs",
        fileTree {
            dir("tests") {
                dir("a") {
                    rust("main.rs", "")
                }
            }
        }
    )

    private fun checkAvailable(target: String, before: FileTree, after: FileTree) {
        val file = before.create().psiFile(target)
        testActionOnElement(file, shouldBeEnabled = true)
        after.assertEquals(myFixture.findFileInTempDir("."))
    }

    private fun checkNotAvailable(target: String, before: FileTree) {
        val file = before.create().psiFile(target)
        testActionOnElement(file, shouldBeEnabled = false)
    }

    private fun testActionOnElement(element: PsiElement, shouldBeEnabled: Boolean) {
        myFixture.launchAction(
            "Rust.RsPromoteModuleToDirectoryAction",
            CommonDataKeys.PSI_ELEMENT to element,
            shouldBeEnabled = shouldBeEnabled
        )
    }
}

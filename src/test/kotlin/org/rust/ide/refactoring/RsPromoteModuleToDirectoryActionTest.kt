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

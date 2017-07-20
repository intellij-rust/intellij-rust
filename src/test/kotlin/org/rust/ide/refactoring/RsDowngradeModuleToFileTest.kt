/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.psi.PsiElement
import com.intellij.testFramework.TestDataProvider
import org.rust.FileTree
import org.rust.fileTree
import org.rust.lang.RsTestBase
import org.rust.lang.refactoring.RsDowngradeModuleToFile

class RsDowngradeModuleToFileTest : RsTestBase() {
    fun `test works on file`() = checkAvailable(
        "foo/mod.rs",
        fileTree {
            dir("foo") {
                rust("mod.rs", "fn hello() {}")
            }
        },
        fileTree {
            rust("foo.rs", "fn hello() {}")
        }
    )


    fun `test works on directory`() = checkAvailable(
        "foo",
        fileTree {
            dir("foo") {
                rust("mod.rs", "fn hello() {}")
            }
        },
        fileTree {
            rust("foo.rs", "fn hello() {}")
        }
    )


    fun `test not available on wrong file`() = checkNotAvailable(
        "foo/bar.rs",
        fileTree {
            dir("foo") {
                rust("mod.rs", "")
                rust("bar.rs", "")
            }
        }
    )

    fun `test not available on full directory`() = checkNotAvailable(
        "foo/mod.rs",
        fileTree {
            dir("foo") {
                rust("mod.rs", "")
                rust("bar.rs", "")
            }
        }
    )

    fun checkAvailable(target: String, before: FileTree, after: FileTree) {
        val file = before.create().psiFile(target)
        testActionOnElement(file)
        after.assertEquals(myFixture.findFileInTempDir("."))
    }

    fun checkNotAvailable(target: String, before: FileTree) {
        val file = before.create().psiFile(target)
        val presentation = testActionOnElement(file)
        check(!presentation.isEnabled)
    }

    private fun testActionOnElement(element: PsiElement): Presentation {
        com.intellij.idea.IdeaTestApplication.getInstance().setDataProvider(object : TestDataProvider(project) {
            override fun getData(dataId: String?): Any? =
                if (com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT.`is`(dataId)) element else super.getData(dataId)
        })

        return myFixture.testAction(RsDowngradeModuleToFile())
    }
}

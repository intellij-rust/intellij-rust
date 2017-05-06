package org.rust.ide.refactoring

import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.psi.PsiElement
import com.intellij.testFramework.TestDataProvider
import org.rust.FileTree
import org.rust.fileTree
import org.rust.lang.RsTestBase
import org.rust.lang.refactoring.RsPromoteModuleToDirectoryAction

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
        val baseDir = myFixture.findFileInTempDir(".")
        val file = before.create(project, baseDir).psiFile(target)
        testActionOnElement(file)
        after.assertEquals(baseDir)
    }

    private fun checkNotAvailable(target: String, before: FileTree) {
        val baseDir = myFixture.findFileInTempDir(".")
        val file = before.create(project, baseDir).psiFile(target)
        val presentation = testActionOnElement(file)
        check(!presentation.isEnabled)
    }

    private fun testActionOnElement(element: PsiElement): Presentation {
        IdeaTestApplication.getInstance().setDataProvider(object : TestDataProvider(project) {
            override fun getData(dataId: String?): Any? =
                if (CommonDataKeys.PSI_ELEMENT.`is`(dataId)) element else super.getData(dataId)
        })

        return myFixture.testAction(RsPromoteModuleToDirectoryAction())
    }
}

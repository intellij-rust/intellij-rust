package org.rust.ide.actions

import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.psi.PsiElement
import com.intellij.testFramework.TestDataProvider
import org.rust.FileTree
import org.rust.fileTree
import org.rust.lang.RsTestBase

class RsContractModuleActionTest : RsTestBase() {
    override val dataPath: String = ""

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
        val baseDir = myFixture.findFileInTempDir(".")
        val file = before.create(project, baseDir).psiFile(target)
        testActionOnElement(file)
        after.assertEquals(baseDir)
    }

    fun checkNotAvailable(target: String, before: FileTree) {
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

        return myFixture.testAction(RsContractModuleAction())
    }
}

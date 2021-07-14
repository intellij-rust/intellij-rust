/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.rust.*

class RsCompletionTestFixture(
    fixture: CodeInsightTestFixture,
    private val defaultFileName: String = "main.rs"
) : RsCompletionTestFixtureBase<String>(fixture) {

    override fun prepare(code: String) {
        InlineFile(myFixture, code.trimIndent(), defaultFileName).withCaret()
    }

    fun doSingleCompletionByFileTree(before: String, after: String) =
        doSingleCompletionByFileTree(fileTreeFromText(before), after)

    fun doSingleCompletionByFileTree(fileTree: FileTree, after: String, forbidAstLoading: Boolean = true) {
        val testProject = fileTree.createAndOpenFileWithCaretMarker(myFixture)
        if (forbidAstLoading) {
            checkAstNotLoaded { file ->
                !file.path.endsWith(testProject.fileWithCaret)
            }
        }
        executeSoloCompletion()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    fun checkNoCompletionByFileTree(code: String) {
        val testProject = fileTreeFromText(code).createAndOpenFileWithCaretMarker(myFixture)
        checkAstNotLoaded { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        }
        noCompletionCheck()
    }

    fun checkContainsCompletionByFileTree(
        code: String,
        variants: List<String>,
        render: LookupElement.() -> String = { lookupString }
    ) {
        fileTreeFromText(code).createAndOpenFileWithCaretMarker(myFixture)
        doContainsCompletion(variants.toSet(), render)
    }

    private fun checkAstNotLoaded(fileFilter: VirtualFileFilter) {
        PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(fileFilter, testRootDisposable)
    }
}

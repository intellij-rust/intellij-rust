/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.BaseFixture
import org.intellij.lang.annotations.Language
import org.rust.*

class RsCompletionTestFixture(
    private val myFixture: CodeInsightTestFixture,
    private val defaultFileName: String = "main.rs"
) : BaseFixture() {

    private val project: Project get() = myFixture.project

    fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()

        if (variants != null) {
            if (variants.size == 1) {
                // for cases like `frob/*caret*/nicate()`,
                // completion won't be selected automatically.
                myFixture.type('\n')
                return
            }
            fun LookupElement.debug(): String = "$lookupString ($psiElement)"
            error("Expected a single completion, but got ${variants.size}\n"
                + variants.joinToString("\n") { it.debug() })
        }
    }

    fun doFirstCompletion(before: String, after: String) {
        check(hasCaretMarker(before) && hasCaretMarker(after)) {
            "Please add `/*caret*/` or `<caret>` marker"
        }
        checkByText(before.trimIndent(), after.trimIndent()) {
            val variants = myFixture.completeBasic()
            if (variants != null) {
                myFixture.type('\n')
            }
        }
    }

    fun doSingleCompletion(before: String, after: String) {
        check(hasCaretMarker(before) && hasCaretMarker(after)) {
            "Please add `/*caret*/` or `<caret>` marker"
        }
        checkByText(before.trimIndent(), after.trimIndent()) { executeSoloCompletion() }
    }

    fun doSingleCompletionByFileTree(before: String, after: String) =
        doSingleCompletionByFileTree(fileTreeFromText(before), after)

    fun doSingleCompletionByFileTree(fileTree: FileTree, after: String) {
        val testProject = fileTree.createAndOpenFileWithCaretMarker(myFixture)
        checkAstNotLoaded(VirtualFileFilter { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        })
        executeSoloCompletion()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    fun checkNoCompletion(@Language("Rust") code: String) {
        InlineFile(code).withCaret()
        noCompletionCheck()
    }

    fun checkNoCompletionByFileTree(code: String) {
        val testProject = fileTreeFromText(code).createAndOpenFileWithCaretMarker(myFixture)
        checkAstNotLoaded(VirtualFileFilter { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        })
        noCompletionCheck()
    }

    private fun noCompletionCheck() {
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
            "Expected zero completions, but one completion was auto inserted: `${element?.text}`."
        }
        check(variants.isEmpty()) {
            "Expected zero completions, got ${variants.size}."
        }
    }

    fun checkContainsCompletion(text: String, code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            "Expected completions that contain $text, but no completions found"
        }
        if (variants.all { it.lookupString != text }) {
            error("Expected completions that contain $text, but got ${variants.toList()}")
        }
    }

    fun checkNotContainsCompletion(text: String, code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            "Expected completions that contain $text, but no completions found"
        }
        if (variants.any { it.lookupString == text }) {
            error("Expected completions that don't contain $text, but got ${variants.toList()}")
        }
    }

    @Suppress("TestFunctionName")
    private fun InlineFile(code: String): InlineFile = InlineFile(myFixture, code, defaultFileName)

    private fun checkByText(before: String, after: String, action: () -> Unit) {
        InlineFile(before)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    private fun checkAstNotLoaded(fileFilter: VirtualFileFilter) {
        PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(fileFilter, testRootDisposable)
    }
}

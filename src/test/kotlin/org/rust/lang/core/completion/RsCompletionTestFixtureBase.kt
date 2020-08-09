/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapiext.Testmark
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.BaseFixture
import org.intellij.lang.annotations.Language
import org.rust.*

abstract class RsCompletionTestFixtureBase<IN>(
    protected val myFixture: CodeInsightTestFixture
) : BaseFixture() {

    private val project: Project get() = myFixture.project

    fun executeSoloCompletion() {
        val lookups = myFixture.completeBasic()

        if (lookups != null) {
            if (lookups.size == 1) {
                // for cases like `frob/*caret*/nicate()`,
                // completion won't be selected automatically.
                myFixture.type('\n')
                return
            }
            fun LookupElement.debug(): String = "$lookupString ($psiElement)"
            error("Expected a single completion, but got ${lookups.size}\n"
                + lookups.joinToString("\n") { it.debug() })
        }
    }

    fun doFirstCompletion(code: IN, after: String) {
        check(hasCaretMarker(after))
        checkByText(code, after.trimIndent()) {
            val variants = myFixture.completeBasic()
            if (variants != null) {
                myFixture.type('\n')
            }
        }
    }

    fun doSingleCompletion(code: IN, after: String) {
        check(hasCaretMarker(after))
        checkByText(code, after.trimIndent()) { executeSoloCompletion() }
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

    fun checkCompletion(
        lookupString: String,
        before: IN,
        @Language("Rust") after: String,
        completionChar: Char,
        testmark: Testmark?
    ) {
        val action = {
            checkByText(before, after.trimIndent()) {
                val items = myFixture.completeBasic()
                    ?: return@checkByText // single completion was inserted
                val lookupItem = items.find { it.lookupString == lookupString } ?: return@checkByText
                myFixture.lookup.currentItem = lookupItem
                myFixture.type(completionChar)
            }
        }
        testmark?.checkHit(action)
    }

    fun checkNoCompletion(code: IN) {
        prepare(code)
        noCompletionCheck()
    }

    fun checkNoCompletionByFileTree(code: String) {
        val testProject = fileTreeFromText(code).createAndOpenFileWithCaretMarker(myFixture)
        checkAstNotLoaded(VirtualFileFilter { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        })
        noCompletionCheck()
    }

    protected fun noCompletionCheck() {
        val lookups = myFixture.completeBasic()
        checkNotNull(lookups) {
            val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
            "Expected zero completions, but one completion was auto inserted: `${element?.text}`."
        }
        check(lookups.isEmpty()) {
            "Expected zero completions, got ${lookups.size}."
        }
    }

    fun checkContainsCompletion(code: IN, variant: String) = checkContainsCompletion(code, listOf(variant))

    fun checkContainsCompletion(code: IN, variants: List<String>) {
        prepare(code)
        val lookups = myFixture.completeBasic()

        checkNotNull(lookups) {
            "Expected completions that contain $variants, but no completions found"
        }
        for (variant in variants) {
            if (lookups.all { it.lookupString != variant }) {
                error("Expected completions that contain $variant, but got ${lookups.map { it.lookupString }}")
            }
        }
    }

    fun checkNotContainsCompletion(code: IN, variant: String) {
        prepare(code)
        val lookups = myFixture.completeBasic()
        checkNotNull(lookups) {
            "Expected completions that contain $variant, but no completions found"
        }
        if (lookups.any { it.lookupString == variant }) {
            error("Expected completions that don't contain $variant, but got ${lookups.map { it.lookupString }}")
        }
    }

    protected fun checkByText(code: IN, after: String, action: () -> Unit) {
        prepare(code)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    private fun checkAstNotLoaded(fileFilter: VirtualFileFilter) {
        PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(fileFilter, testRootDisposable)
    }

    protected abstract fun prepare(code: IN)
}

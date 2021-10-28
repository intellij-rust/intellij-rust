/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.openapiext.Testmark

abstract class RsCompletionTestBase(private val defaultFileName: String = "main.rs") : RsTestBase() {

    protected lateinit var completionFixture: RsCompletionTestFixture

    override fun setUp() {
        super.setUp()
        completionFixture = RsCompletionTestFixture(myFixture, defaultFileName)
        completionFixture.setUp()
    }

    override fun tearDown() {
        completionFixture.tearDown()
        super.tearDown()
    }

    // Prefer using `doSingleCompletion` instead
    @Deprecated(
        "Use doSingleCompletion, because it's simpler and checks caret position as well",
        replaceWith = ReplaceWith("doSingleCompletion(code, code)")
    )
    protected fun checkSingleCompletion(target: String, @Language("Rust") code: String) {
        InlineFile(code).withCaret()
        executeSoloCompletion()

        val normName = target
            .substringBeforeLast("()")
            .substringBeforeLast(" {}")
            .substringAfterLast("::")
            .substringAfterLast(".")

        val shift = when {
            target.endsWith("()") || target.endsWith("::") -> 3
            target.endsWith(" {}") -> 4
            else -> 2
        }
        val element = myFixture.file.findElementAt(myFixture.caretOffset - shift)!!
        val skipTextCheck = normName.isEmpty() || normName.contains(' ')
        check((skipTextCheck || element.text == normName) && (element.fitsHierarchically(target) || element.fitsLinearly(target))) {
            "Wrong completion, expected `$target`, but got\n${myFixture.file.text}"
        }
    }

    protected fun doFirstCompletion(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = completionFixture.doFirstCompletion(before, after)

    protected fun doSingleCompletion(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = completionFixture.doSingleCompletion(before, after)

    protected fun doSingleCompletionByFileTree(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = completionFixture.doSingleCompletionByFileTree(before, after)

    protected fun checkContainsCompletion(
        variant: String,
        @Language("Rust") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkContainsCompletion(code, listOf(variant), render)

    protected fun checkContainsCompletion(
        variants: List<String>,
        @Language("Rust") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkContainsCompletion(code, variants, render)

    protected fun checkContainsCompletionByFileTree(
        variants: List<String>,
        @Language("Rust") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkContainsCompletionByFileTree(code, variants, render)

    protected fun checkContainsCompletionPrefixes(
        prefixes: List<String>,
        @Language("Rust") code: String
    ) = completionFixture.checkContainsCompletionPrefixes(code, prefixes)

    protected fun checkCompletion(
        lookupString: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        completionChar: Char = '\n',
        testmark: Testmark? = null
    ) = completionFixture.checkCompletion(lookupString, before, after, completionChar, testmark)

    protected fun checkNotContainsCompletion(
        variant: String,
        @Language("Rust") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkNotContainsCompletion(code, setOf(variant), render)

    protected fun checkNotContainsCompletion(
        variants: Set<String>,
        @Language("Rust") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkNotContainsCompletion(code, variants, render)

    protected fun checkNotContainsCompletion(
        variants: List<String>,
        @Language("Rust") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) {
        completionFixture.checkNotContainsCompletion(code, variants.toSet(), render)
    }

    protected open fun checkNoCompletion(@Language("Rust") code: String) = completionFixture.checkNoCompletion(code)

    protected fun checkNoCompletionByFileTree(@Language("Rust") code: String) =
        completionFixture.checkNoCompletionByFileTree(code)

    protected fun executeSoloCompletion() = completionFixture.executeSoloCompletion()

    private fun PsiElement.fitsHierarchically(target: String): Boolean = when {
        text == target -> true
        text.length > target.length -> false
        parent != null -> parent.fitsHierarchically(target)
        else -> false
    }

    private fun PsiElement.fitsLinearly(target: String) =
        checkLinearly(target, Direction.LEFT) || checkLinearly(target, Direction.RIGHT)

    private fun PsiElement.checkLinearly(target: String, direction: Direction): Boolean {
        var el = this
        var text = ""
        while (text.length < target.length) {
            text = if (direction == Direction.LEFT) el.text + text else text + el.text
            if (text == target) return true
            el = (if (direction == Direction.LEFT) PsiTreeUtil.prevVisibleLeaf(el) else PsiTreeUtil.nextVisibleLeaf(el)) ?: break
        }
        return false
    }

    private enum class Direction {
        LEFT,
        RIGHT
    }
}

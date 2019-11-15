/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

abstract class RsCompletionTestBase : RsTestBase() {

    protected lateinit var completionFixture: RsCompletionTestFixture

    override fun setUp() {
        super.setUp()
        completionFixture = RsCompletionTestFixture(myFixture)
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
        text: String,
        @Language("Rust") code: String
    ) = completionFixture.checkContainsCompletion(text, code)

    protected fun checkNotContainsCompletion(
        text: String,
        @Language("Rust") code: String
    ) = completionFixture.checkNotContainsCompletion(text, code)

    protected fun checkNoCompletion(@Language("Rust") code: String) = completionFixture.checkNoCompletion(code)

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

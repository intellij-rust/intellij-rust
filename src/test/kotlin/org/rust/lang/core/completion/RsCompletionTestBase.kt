/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.lang.annotations.Language
import org.rust.FileTree
import org.rust.lang.RsTestBase

abstract class RsCompletionTestBase : RsTestBase() {
    protected fun checkSingleCompletion(target: String, @Language("Rust") code: String) {
        InlineFile(code).withCaret()
        singleCompletionCheck(target)
    }

    protected fun doSingleCompletion(@Language("Rust") before: String, @Language("Rust") after: String) {
        checkByText(before, after) { executeSoloCompletion() }
    }

    fun doSingleCompletion(before: FileTree, @Language("Rust") after: String) {
        val baseDir = myFixture.findFileInTempDir(".")
        val testProject = before.create(project, baseDir)
        val fileWithCaret = testProject.fileWithCaret ?: error("No /*caret*/ found")
        myFixture.configureFromTempProjectFile(fileWithCaret)
        executeSoloCompletion()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    protected fun checkSingleCompletionWithMultipleFiles(target: String, @Language("Rust") code: String) {
        val files = ProjectFile.parseFileCollection(code)
        for ((path, text) in files) {
            myFixture.tempDirFixture.createFile(path, replaceCaretMarker(text))
        }

        openFileInEditor(files[0].path)

        singleCompletionCheck(target)
    }

    protected fun singleCompletionCheck(target: String) {
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

    protected fun checkContainsCompletion(text: String, @Language("Rust") code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            "Expected completions that contain $text, but no completions found"
        }
        variants.filter { it.lookupString == text }.forEach { return }
        error("Expected completions that contain $text, but got ${variants.toList()}")
    }

    protected fun checkNoCompletion(@Language("Rust") code: String) {
        InlineFile(code).withCaret()
        noCompletionCheck()
    }

    protected fun checkNoCompletionWithMultipleFiles(@Language("Rust") code: String) {
        val files = ProjectFile.parseFileCollection(code)
        for ((path, text) in files) {
            myFixture.tempDirFixture.createFile(path, replaceCaretMarker(text))
        }

        openFileInEditor(files[0].path)
        noCompletionCheck()
    }

    protected fun noCompletionCheck() {
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
            "Expected zero completions, but one completion was auto inserted: `${element?.text}`."
        }
        check(variants.isEmpty()) {
            "Expected zero completions, got ${variants.size}."
        }
    }

    protected fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()

        if (variants != null) {
            fun LookupElement.debug(): String = "$lookupString ($psiElement)"
            error("Expected a single completion, but got ${variants.size}\n"
                + variants.map { it.debug() }.joinToString("\n"))
        }
    }

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

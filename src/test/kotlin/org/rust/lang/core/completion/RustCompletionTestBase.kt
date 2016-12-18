package org.rust.lang.core.completion

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

abstract class RustCompletionTestBase : RustTestCaseBase() {

    protected fun checkSingleCompletionByFile() = checkByFile {
        executeSoloCompletion()
    }

    protected fun checkSingleCompletion(target: String, @Language("Rust") code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        check(variants == null) {
            "Expected a single completion, but got ${variants.size}\n" + "${variants.toList()}"
        }
        val parentheses = target.endsWith("()")
        val shift = if (parentheses) 3 else 1
        val element = myFixture.file.findElementAt(myFixture.caretOffset - shift)!!
        check(element.correspondsToText(target)) {
            "Wrong completion, expected `$target`, but got `${element.text}`"
        }
    }

    protected fun checkNoCompletion(@Language("Rust") code: String) {
        InlineFile(code).withCaret()
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
            error("Expected a single completion, but got ${variants.size}\n" +
                "${variants.toList()}")
        }
    }

    private fun PsiElement.correspondsToText(target: String): Boolean {
        return when {
            text == target -> true
            text.length > target.length -> false
            parent != null -> parent.correspondsToText(target)
            else -> false
        }
    }
}

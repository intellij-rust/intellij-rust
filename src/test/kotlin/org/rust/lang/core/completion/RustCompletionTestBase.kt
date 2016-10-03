package org.rust.lang.core.completion

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
        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)!!
        check(element.text == target) {
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
        check(variants.size == 0) {
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
}

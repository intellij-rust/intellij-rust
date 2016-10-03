package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

abstract class RustCompletionTestBase : RustTestCaseBase() {

    protected fun checkSoleCompletion() = checkByFile {
        executeSoloCompletion()
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

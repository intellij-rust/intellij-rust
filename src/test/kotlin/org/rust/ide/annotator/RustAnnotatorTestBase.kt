package org.rust.ide.annotator

import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

abstract class RustAnnotatorTestBase : RustTestCaseBase() {
    override val dataPath = ""

    protected fun doTest(vararg additionalFilenames: String) {
        myFixture.testHighlighting(fileName, *additionalFilenames)
    }

    protected fun checkInfo(text: String) {
        myFixture.configureByText("main.rs", text)
        myFixture.testHighlighting(false, true, false)
    }

    protected fun checkWarnings(text: String) {
        myFixture.configureByText("main.rs", text)
        myFixture.testHighlighting(true, false, true)
    }

    protected fun checkQuickFix(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = checkByText(before, after) { applyQuickFix(fixName) }

    protected fun checkQuickFix(fixName: String) = checkByFile { applyQuickFix(fixName) }
}


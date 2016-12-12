package org.rust.ide.annotator

import org.rust.lang.RustTestCaseBase

abstract class RustAnnotatorTestBase: RustTestCaseBase() {
    override val dataPath = ""

    protected fun doTest(vararg additionalFilenames: String) {
        myFixture.testHighlighting(fileName, *additionalFilenames)
    }

    protected fun checkInfo(text: String) {
        myFixture.configureByText("main.rs", text)
        myFixture.testHighlighting(false, true, false)
    }

    protected fun checkQuickFix(fixName: String) = checkByFile { applyQuickFix(fixName) }
}


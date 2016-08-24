package org.rust.ide.annotator

import org.rust.lang.RustTestCaseBase

abstract class RustAnnotatorTestBase: RustTestCaseBase() {

    protected fun doTest(vararg additionalFilenames: String) {
        myFixture.testHighlighting(fileName, *additionalFilenames)
    }

    protected fun doTestInfo() {
        myFixture.testHighlighting(false, true, false, fileName)
    }

    protected fun checkQuickFix(fixName: String) = checkByFile { applyQuickFix(fixName) }
}


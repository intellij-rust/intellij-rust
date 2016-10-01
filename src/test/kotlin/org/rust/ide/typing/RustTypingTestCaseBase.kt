package org.rust.ide.typing

import org.rust.lang.RustTestCaseBase

abstract class RustTypingTestCaseBase : RustTestCaseBase() {
    protected fun doTest(c: Char = '\n') = checkByFile {
        myFixture.type(c)
    }

    protected fun doTestByText(fileName: String, before: String, after: String, c: Char) =
        checkByText(fileName, before, after) {
            myFixture.type(c)
        }
}

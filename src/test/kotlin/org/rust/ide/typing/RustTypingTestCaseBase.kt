package org.rust.ide.typing

import org.rust.lang.RustTestCaseBase

abstract class RustTypingTestCaseBase : RustTestCaseBase() {
    protected fun doTest(c: Char = '\n') = checkByFile {
        myFixture.type(c)
    }

    protected fun doTestByText(before: String, after: String, c: Char) =
        checkByText(before, after) {
            myFixture.type(c)
        }
}

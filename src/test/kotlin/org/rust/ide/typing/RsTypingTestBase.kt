package org.rust.ide.typing

import org.rust.lang.RsTestBase

abstract class RsTypingTestBase : RsTestBase() {
    protected fun doTest(c: Char = '\n') = checkByFile {
        myFixture.type(c)
    }

    protected fun doTestByText(before: String, after: String, c: Char) =
        checkByText(before, after) {
            myFixture.type(c)
        }
}

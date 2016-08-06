package org.rust.ide.typing

import org.rust.lang.RustTestCaseBase

abstract class RustTypingTestCaseBase : RustTestCaseBase() {
    protected fun doTest(c: Char = '\n') = checkByFile {
        myFixture.type(c)
    }
}

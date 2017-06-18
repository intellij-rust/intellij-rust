package org.rust.ide.typing

import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

abstract class RsTypingTestBase : RsTestBase() {
    protected fun doTest(c: Char = '\n') = checkByFile {
        myFixture.type(c)
    }

    protected fun doTestByText(@Language("Rust") before: String, @Language("Rust") after: String, c: Char) =
        checkByText(before, after) {
            myFixture.type(c)
        }
}

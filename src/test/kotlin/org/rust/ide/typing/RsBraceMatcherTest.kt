package org.rust.ide.typing

import org.rust.lang.RustFileType
import org.rust.lang.RsTestBase

class RsBraceMatcherTest : RsTestBase() {
    override val dataPath: String get() = ""

    fun testBeforeIdent() = doTest(
        "fn main() { let _ = <caret>typing }",
        '(',
        "fn main() { let _ = (<caret>typing }"
    )

    fun testBeforeSemicolon() = doTest(
        "fn main() { let _ = <caret>; }",
        '(',
        "fn main() { let _ = (<caret>); }"
    )

    fun testBeforeBrace() = doTest(
        "fn foo<caret>{}",
        '(',
        "fn foo(<caret>){}"
    )

    private fun doTest(before: String, type: Char, after: String) {
        myFixture.configureByText(RustFileType, before)
        myFixture.type(type)
        myFixture.checkResult(after)
    }
}


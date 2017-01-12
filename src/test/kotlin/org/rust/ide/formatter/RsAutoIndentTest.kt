package org.rust.ide.formatter

import org.rust.ide.typing.RsTypingTestBase

class RsAutoIndentTest : RsTypingTestBase() {
    override val dataPath = "org/rust/ide/formatter/fixtures/auto_indent"

    fun testFn() = doTest()
    fun testIf() = doTest()
    fun testModItem() = doTest()
    fun testModItem2() = doTest()
    fun testForeignModItem() = doTest()
    fun testForeignModItem2() = doTest()
    fun testPat() = doTest()
    fun testChainCall() = doTest()
    fun testChainCall2() = doTest()
    fun testChainCall3() = doTest()
    fun testChainCall4() = doTest()
    fun testChainCall5() = doTest()
    fun testExpr() = doTest()
    fun testExpr2() = doTest()
    fun testExpr3() = doTest()

    fun testIssue475() = doTest('(')    // https://github.com/intellij-rust/intellij-rust/issues/475
}

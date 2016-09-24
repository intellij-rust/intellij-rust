package org.rust.ide.typing

class RustRawLiteralHashesDeleterTest : RustTypingTestCaseBase() {
    override val dataPath = "org/rust/ide/typing/rawHashes/fixtures"
    override val inverse = true

    fun testSimpleOpen() = doTest('\b')
    fun testSimpleClose() = doTest('\b')
    fun testBeforeR() = doTest('\b')
    fun testAfterR() = doTest('\b')
    fun testBeforeQuote() = doTest('\b')
    fun testAfterQuote() = doTest('\b')
    fun testAtEnd() = doTest('\b')
    fun testAtTotalEnd() = doTest('\b')
    fun testInsideValue() = doTest('\b')
    fun testNoQuotes() = doTest('\b')
    fun testNoQuotes2() = doTest('\b')
    fun testMulticursor() = doTest('\b')
}

package org.rust.ide.typing

class RustRawLiteralHashesInserterTest : RustTypingTestCaseBase() {
    override val dataPath = "org/rust/ide/typing/rawHashes/insert/fixtures"

    fun testSimpleOpen() = doTest('#')
    fun testSimpleClose() = doTest('#')
    fun testBeforeR() = doTest('#')
    fun testAfterR() = doTest('#')
    fun testBeforeQuote() = doTest('#')
    fun testAfterQuote() = doTest('#')
    fun testAtEnd() = doTest('#')
    fun testAtTotalEnd() = doTest('#')
    fun testInsideValue() = doTest('#')
    fun testNoQuotes() = doTest('#')
    fun testNoQuotes2() = doTest('#')
}

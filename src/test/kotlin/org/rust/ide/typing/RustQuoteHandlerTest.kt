package org.rust.ide.typing

class RustQuoteHandlerTest : RustTypingTestCaseBase() {
    override val dataPath = "org/rust/ide/typing/quoteHandler/fixtures"

    fun testDoNotCompleteCharQuotes() = doTest('\'')
    fun testDoNotCompleteByteQuotes() = doTest('\'')

    fun testCompleteStringQuotes() = doTest('"')
    fun testCompleteByteStringQuotes() = doTest('"')

    fun testCompleteRawStringQuotes() = doTest('"')
    fun testCompleteRawStringQuotes2() = doTest('"')
    fun testCompleteByteRawStringQuotes() = doTest('"')
    fun testCompleteByteRawStringQuotes2() = doTest('"')
}

package org.rust.ide.typing

class RsQuoteHandlerTest : RsTypingTestBase() {
    override val dataPath = "org/rust/ide/typing/quoteHandler/fixtures"

    fun testDoNotCompleteCharQuotes() = doTest('\'')
    fun testDoNotCompleteByteQuotes() = doTest('\'')

    fun testCompleteStringQuotes() = doTest('"')
    fun testCompleteByteStringQuotes() = doTest('"')

    fun testCompleteRawStringQuotes() = doTest('"')
    fun testCompleteRawStringQuotes2() = doTest('"')
    fun testCompleteByteRawStringQuotes() = doTest('"')
    fun testCompleteByteRawStringQuotes2() = doTest('"')

    // https://github.com/intellij-rust/intellij-rust/issues/687
    fun testDoubleQuoteInRawLiteral() = doTest('"')

    fun testSingleQuoteInRawLiteral() = doTest('\'')
}

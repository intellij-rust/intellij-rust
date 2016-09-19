package org.rust.ide.typing

class RustEnterInStringLiteralHandlerTest : RustTypingTestCaseBase() {
    override val dataPath = "org/rust/ide/typing/string/fixtures"

    fun testSimple() = doTest()

    fun testBeforeOpening() = doTest()
    fun testAfterOpening() = doTest()
    fun testInsideOpening() = doTest()
    fun testBeforeClosing() = doTest()
    fun testAfterClosing() = doTest()

    fun testRawLiteral() = doTest()

    fun testBeforeEscape() = doTest()
    fun testAfterEscape() = doTest()
    fun testInsideEscape() = doTest()
    fun testInsideEscapeAfterSlash() = doTest()
    fun testBetweenQuoteEscape() = doTest()
    fun testBetweenEscapeQuote() = doTest()

    fun testIncomplete() = doTest()
    fun testIncompleteEscape() = doTest()
    fun testRawIncomplete() = doTest()
}

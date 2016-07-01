package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer

class RustHighlightingLexingTestCase : RustLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures/highlighting"

    override fun createLexer(): Lexer = RustHighlightingLexer()

    fun testEol() = doTest()
    fun testRawLiterals() = doTest()

    fun testLineDoc() = doTest()
    fun testBlockDoc() = doTest()
    fun testDocHeading() = doTest()
    fun testDocLink() = doTest()
    fun testDocCodeSpan() = doTest()
    fun testDocCodeFence() = doTest()
    fun testHeaderAfterCodeFence() = doTest()
}

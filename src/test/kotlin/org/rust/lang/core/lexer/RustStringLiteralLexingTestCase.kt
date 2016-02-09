package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer

public class RustStringLiteralLexingTestCase : RustLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures/escapes"

    override fun createLexer(): Lexer = RustStringLiteralLexer.forStringLiterals()

    fun testShortByteEscapes() = doTest()
    fun testLongByteEscapes() = doTest()
    fun testSampleUnicodeEscapes() = doTest()
    fun testVariableLengthUnicodeEscapes() = doTest()
    fun testInvalidShortByteEscapes() = doTest()
    fun testInvalidLongByteEscapes() = doTest()
    fun testInvalidUnicodeEscapes() = doTest()
    fun testMixed() = doTest()
    fun testMixedQuoted() = doTest()
}

package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer

class RustLexingTestCase : RustLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures"

    override fun createLexer(): Lexer? = RustLexer()

    fun testComments() = doTest()
    fun testShebang() = doTest()
    fun testShebang1() = doTest()
    fun testShebang2() = doTest()
    fun testShebang3() = doTest()
    fun testShebang4() = doTest()
    fun testNumbers() = doTest()
    fun testIdentifiers() = doTest()
    fun testCharLiterals() = doTest()
    fun testStringLiterals() = doTest()
    fun testByteLiterals() = doTest()
    fun testInvalidEscape() = doTest()
}

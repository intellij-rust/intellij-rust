package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import java.util.*

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

    fun testFuzzy() {
        val lexers = listOf(
            RustStringLiteralLexer.forByteLiterals(),
            RustStringLiteralLexer.forByteStringLiterals(),
            RustStringLiteralLexer.forCharLiterals(),
            RustStringLiteralLexer.forStringLiterals()
        )

        for (lexer in lexers) {
            repeat(10000) {
                execute(lexer, randomLiteral())
            }
        }
    }

    private fun execute(lexer: RustStringLiteralLexer, input: CharSequence)  {
        lexer.start(input)
        while (lexer.tokenType != null) {
            lexer.advance()
        }
    }

    private fun randomLiteral(): String {
        val random = Random()
        val length = random.nextInt(10)
        val chars = """\xu"vnrt0'"""
        val xs = CharArray(length, {
            chars[random.nextInt(chars.length)]
        })
        return String(xs)
    }
}

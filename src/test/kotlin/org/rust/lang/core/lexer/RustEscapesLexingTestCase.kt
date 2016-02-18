package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import org.rust.lang.core.psi.RustTokenElementTypes.ESCAPABLE_LITERALS_TOKEN_SET
import org.rust.lang.core.psi.RustTokenElementTypes.STRING_LITERAL
import java.util.*

class RustEscapesLexingTestCase : RustLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures/escapes"

    override fun createLexer(): Lexer = RustEscapesLexer.of(STRING_LITERAL)

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
        val lexers = ESCAPABLE_LITERALS_TOKEN_SET.types.map { RustEscapesLexer.of(it) }

        for (lexer in lexers) {
            repeat(10000) {
                execute(lexer, randomLiteral())
            }
        }
    }

    private fun execute(lexer: RustEscapesLexer, input: CharSequence) {
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

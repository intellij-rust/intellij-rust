/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import org.rust.lang.core.lexer.RsEscapesLexer.Companion.ESCAPABLE_LITERALS_TOKEN_SET
import org.rust.lang.core.psi.RsElementTypes.BYTE_STRING_LITERAL
import org.rust.lang.core.psi.RsElementTypes.STRING_LITERAL
import java.util.*

class RsEscapesLexingTestCase : RsLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures/escapes"

    // We want unicode and eol but not extended escapes. String literals are perfect.
    override fun createLexer(): Lexer = RsEscapesLexer.of(STRING_LITERAL)

    fun testShortByteEscapes() = doTest()
    fun testLongByteEscapes() = doTest()
    fun testExtendedByteEscapes() = doTest(RsEscapesLexer.of(BYTE_STRING_LITERAL))
    fun testSampleUnicodeEscapes() = doTest()
    fun testVariableLengthUnicodeEscapes() = doTest()
    fun testInvalidShortByteEscapes() = doTest()
    fun testInvalidLongByteEscapes() = doTest()
    fun testInvalidUnicodeEscapes() = doTest()
    fun testMixed() = doTest()
    fun testMixedQuoted() = doTest()
    fun testUnderscoreInUnicodeEscapes() = doTest()

    fun testFuzzy() {
        val lexers = ESCAPABLE_LITERALS_TOKEN_SET.types.map { RsEscapesLexer.of(it) }
        for (lexer in lexers) {
            repeat(10000) {
                randomLiteral().tokenize(lexer).forEach { }
            }
        }
    }

    private fun randomLiteral(): String {
        val random = Random()
        val length = random.nextInt(10)
        val chars = """\xu"vnrt0'"""
        val xs = CharArray(length) {
            chars[random.nextInt(chars.length)]
        }
        return String(xs)
    }
}

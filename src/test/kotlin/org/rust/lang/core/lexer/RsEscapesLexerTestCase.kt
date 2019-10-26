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

class RsEscapesLexerTestCase : RsLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures/escapes"

    // We want unicode and eol but not extended escapes. String literals are perfect.
    override fun createLexer(): Lexer = RsEscapesLexer.of(STRING_LITERAL)

    fun `test short byte escapes`() = doTest()
    fun `test long byte escapes`() = doTest()
    fun `test extended byte escapes`() = doTest(RsEscapesLexer.of(BYTE_STRING_LITERAL))
    fun `test sample unicode escapes`() = doTest()
    fun `test variable length unicode escapes`() = doTest()
    fun `test invalid short byte escapes`() = doTest()
    fun `test invalid long byte escapes`() = doTest()
    fun `test invalid unicode escapes`() = doTest()
    fun `test mixed`() = doTest()
    fun `test mixed quoted`() = doTest()
    fun `test underscore in unicode escapes`() = doTest()

    fun `test fuzzy`() {
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

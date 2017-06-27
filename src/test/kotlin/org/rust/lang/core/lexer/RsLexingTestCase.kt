/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer

class RsLexingTestCase : RsLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures"

    override fun createLexer(): Lexer? = RsLexer()

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
    fun testDocCommentMerging() = doTest()
    fun testKeywords() = doTest()
}

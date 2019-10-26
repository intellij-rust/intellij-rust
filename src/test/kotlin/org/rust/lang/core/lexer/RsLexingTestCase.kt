/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer

class RsLexingTestCase : RsLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures"

    override fun createLexer(): Lexer? = RsLexer()

    fun `test comments`() = doTest()
    fun `test shebang`() = doTest()
    fun `test shebang 1`() = doTest()
    fun `test shebang 2`() = doTest()
    fun `test shebang 3`() = doTest()
    fun `test shebang 4`() = doTest()
    fun `test numbers`() = doTest()
    fun `test identifiers`() = doTest()
    fun `test char literals`() = doTest()
    fun `test string literals`() = doTest()
    fun `test byte literals`() = doTest()
    fun `test invalid escape`() = doTest()
    fun `test doc comment merging`() = doTest()
    fun `test keywords`() = doTest()
}

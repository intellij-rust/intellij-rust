/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer

class RsHighlightingLexingTestCase : RsLexingTestCaseBase() {
    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures/highlighting"

    override fun createLexer(): Lexer = RsHighlightingLexer()

    fun `test eol`() = doTest()
    fun `test raw literals`() = doTest()

    fun `test line doc`() = doTest()
    fun `test block doc`() = doTest()
    fun `test doc heading`() = doTest()
    fun `test doc link`() = doTest()
    fun `test doc code span`() = doTest()
    fun `test doc code fence`() = doTest()
    fun `test header after code fence`() = doTest()
}

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
}

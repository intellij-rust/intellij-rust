/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

class RsEnterInLineCommentHandlerTest : RsTypingTestBase() {
    override val dataPath = "org/rust/ide/typing/lineComment/fixtures"

    fun `test before line comment`() = doTest()
    fun `test in line comment`() = doTest()
    fun `test after line comment`() = doTest()
    fun `test in block comment`() = doTest()
    fun `test in outer doc comment`() = doTest()
    fun `test after outer doc comment`() = doTest()
    fun `test in inner doc comment`() = doTest()
    fun `test after inner doc comment`() = doTest()
    fun `test after module comment`() = doTest()

    fun `test directly after token`() = doTest()
    fun `test inside token`() = doTest()

    fun `test inside comment directly before next token`() = doTest()
    fun `test inside comment inside token`() = doTest()

    fun `test at file beginning`() = doTest()
    fun `test inside string literal`() = doTest()

    fun `test issue578`() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/578
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.lexer

import com.intellij.openapi.editor.Editor
import org.rust.RsTestBase
import org.rust.ide.typing.createLexer

class RsRestartLexingTestCase : RsTestBase() {
    fun `test lexer restart in raw literal`() =
        doTestLexerRestart("""fn main(){ r##<caret>#"text"### }""", 'a')

    fun `test lexer restart in invalid raw literal`() =
        doTestLexerRestart("""fn main(){ r##a<caret>#"text"### }""", '\b')

    private fun doTestLexerRestart(text: String, char: Char) {
        var editor = createEditor("main.rs", text)
        myFixture.type(char)
        val restartedLexer = editor.createLexer(0)!!

        editor = createEditor("copy.rs", editor.document.text)
        val fullLexer = editor.createLexer(0)!!

        while (!restartedLexer.atEnd() && !fullLexer.atEnd()) {
            assertEquals(restartedLexer.tokenType, fullLexer.tokenType)
            assertEquals(restartedLexer.start, fullLexer.start)
            restartedLexer.advance()
            fullLexer.advance()
        }
        assertTrue(restartedLexer.atEnd() && fullLexer.atEnd())
    }

    private fun createEditor(filename: String, text: String): Editor {
        val file = myFixture.configureByText(filename, text)
        myFixture.openFileInEditor(file.virtualFile)
        return myFixture.editor
    }
}

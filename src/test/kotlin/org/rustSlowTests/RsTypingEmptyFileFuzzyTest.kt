/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.openapi.util.text.StringUtil
import org.rust.ide.typing.RsTypingTestBase

class RsTypingEmptyFileFuzzyTest : RsTypingTestBase() {

    fun `test fuzzy`() {
        // These chars cause problems or require special treating
        val blacklist = setOf('\b', '\t', '\r', '"', '(', '[', '{')
        val chars = (0.toChar()..128.toChar()).filter { it !in blacklist }
        for (char in chars) {
            var backspace = false
            try {
                doTest(char, "<caret>", "$char<caret>")
                backspace = true
                doTest('\b', "$char<caret>", "<caret>")
            } catch (e: Throwable) {
                print("Fuzzy test failed for character '${StringUtil.escapeStringCharacters(char.toString())}'")
                if (backspace) print(" when performing backspace")
                println("!")
                throw e
            }
        }
    }

    private fun doTest(char: Char, before: String, after: String) {
        checkByText(before, after) {
            myFixture.type(char)
        }
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.openapi.util.text.StringUtil

class RsTypingEmptyFileFuzzyTest : RsTypingTestBase() {
    fun testFuzzy() {
        // These chars cause problems or require special treating
        val blacklist = setOf('\b', '\t', '\r', '"', '(', '[', '{')
        val chars = (0.toChar()..128.toChar()).filter { it !in blacklist }
        for (ch in chars) {
            var backspace = false
            try {
                doTestByText("<caret>", "$ch<caret>", ch)
                backspace = true
                doTestByText("$ch<caret>", "<caret>", '\b')
            } catch(e: Throwable) {
                print("Fuzzy test failed for character '${StringUtil.escapeStringCharacters(ch.toString())}'")
                if (backspace) print(" when performing backspace")
                println("!")
                throw e
            }
        }
    }
}

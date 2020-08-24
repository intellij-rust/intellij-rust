/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

// Copy of com.intellij.openapi.externalSystem.service.execution.cmd.ParametersListLexer,
// which is not present in all IDEs.
class ParametersListLexer(private val myText: String) {
    private var myTokenStart = -1
    private var index = 0

    val tokenEnd: Int
        get() {
            assert(myTokenStart >= 0)
            return index
        }

    val currentToken: String
        get() = myText.substring(myTokenStart, index)

    fun nextToken(): Boolean {
        var i = index

        while (i < myText.length && Character.isWhitespace(myText[i])) {
            i++
        }

        if (i == myText.length) return false

        myTokenStart = i
        var isInQuote = false

        do {
            val a = myText[i]
            if (!isInQuote && Character.isWhitespace(a)) break
            when {
                a == '\\' && i + 1 < myText.length && myText[i + 1] == '"' -> i += 2
                a == '"' -> {
                    i++
                    isInQuote = !isInQuote
                }
                else -> i++
            }
        } while (i < myText.length)

        index = i
        return true
    }
}

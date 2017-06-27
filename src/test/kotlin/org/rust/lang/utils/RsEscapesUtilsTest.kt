/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UnescapeRsTest(private val input: String,
                     private val expected: String,
                     private val unicode: Boolean,
                     private val eol: Boolean,
                     private val extendedByte: Boolean) {
    @Test
    fun test() = assertEquals(expected, input.unescapeRust(unicode, eol, extendedByte))

    companion object {
        @Parameterized.Parameters(name = "{index}: \"{0}\" → \"{1}\" U:{2} E:{3}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf("aaa", "aaa", true, true, false),
            arrayOf("aaa", "aaa", false, false, false),
            arrayOf("a\\na", "a\na", true, true, false),
            arrayOf("a\\ra", "a\ra", true, true, false),
            arrayOf("a\\ta", "a\ta", true, true, false),
            arrayOf("a\\0a", "a\u0000a", true, true, false),
            arrayOf("a\\'a", "a'a", true, true, false),
            arrayOf("a\\\"a", "a\"a", true, true, false),
            arrayOf("a\\\\a", "a\\a", true, true, false),
            arrayOf("a\\x20a", "a a", true, true, false),
            arrayOf("a\\x0aa", "a\na", true, true, false),
            arrayOf("a\\x0Aa", "a\na", true, true, false),
            arrayOf("foo\\\n    bar", "foobar", true, true, false),
            arrayOf("foo\\\r\n    bar", "foobar", true, true, false),
            arrayOf("foo\\\r\n    bar", "foo\\\r\n    bar", true, false, false),
            arrayOf("foo\\r\\nbar", "foo\r\nbar", true, true, false),
            arrayOf("\\u{0119}dw\\u{0105}rd", "\u0119dw\u0105rd", true, true, false),
            arrayOf("\\u{0119}dw\\u{0105}rd", "\\u{0119}dw\\u{0105}rd", false, true, false),
            arrayOf("\\u{0}", "\u0000", true, true, false),
            arrayOf("\\u{00}", "\u0000", true, true, false),
            arrayOf("\\u{000}", "\u0000", true, true, false),
            arrayOf("\\u{0000}", "\u0000", true, true, false),
            arrayOf("\\u{00000}", "\u0000", true, true, false),
            arrayOf("\\u{000000}", "\u0000", true, true, false),
            arrayOf("\\u{0000000}", "\\u{0000000}", true, true, false),
            arrayOf("\\u{00000000}", "\\u{00000000}", true, true, false),
            arrayOf("\\u{}", "\\u{}", true, true, false),
            arrayOf("\\u{", "\\u{", true, true, false),
            arrayOf("\\u", "\\u", true, true, false),
            arrayOf("\\u{zzzz}", "\\u{zzzz}", true, true, false),
            arrayOf("\\xff", "\\xff", true, true, false),
            arrayOf("\\xff", "\u00ff", true, true, true)
        )
    }
}

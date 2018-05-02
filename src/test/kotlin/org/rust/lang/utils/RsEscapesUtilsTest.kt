/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class UnescapeRsTest(private val input: String,
                     private val expected: String,
                     private val unicode: Boolean,
                     private val eol: Boolean,
                     private val extendedByte: Boolean) {
    @Test
    fun test() = assertEquals(expected, input.unescapeRust(unicode, eol, extendedByte))

    companion object {
        @Parameters(name = "{index}: \"{0}\" → \"{1}\" U:{2} E:{3}")
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
            arrayOf("\\u{_0}", "\\u{_0}", true, true, false),
            arrayOf("\\u{0_0}", "\u0000", true, true, false),
            arrayOf("\\u{0___0___0}", "\u0000", true, true, false),
            arrayOf("\\u{}", "\\u{}", true, true, false),
            arrayOf("\\u{", "\\u{", true, true, false),
            arrayOf("\\u", "\\u", true, true, false),
            arrayOf("\\u{zzzz}", "\\u{zzzz}", true, true, false),
            arrayOf("\\xff", "\\xff", true, true, false),
            arrayOf("\\xff", "\u00ff", true, true, true)
        )
    }
}

@RunWith(Parameterized::class)
class UnescapeRsWithOffsetsTest(
    private val str: String,
    private val decoded: String,
    private val offsets: IntArray,
    private val success: Boolean
) {
    @Test
    fun test() {
        val (decoded, offsets, success) = parseRustStringCharacters(str)

        assertEquals(this.decoded, decoded.toString())
        assertArrayEquals(this.offsets.copyOf(offsets.size), offsets)
        assertEquals(this.success, success)
    }

    companion object {
        @Parameters(name = "{index}: \"{0}\" → \"{1}\" S:{3}")
        @JvmStatic
        fun data(): Collection<Array<out Any>> = listOf(
            arrayOf("", "", intArrayOf(), true),
            arrayOf("foo", "foo", intArrayOf(0,1,2,3), true),
            arrayOf("""a\"b""", "a\"b", intArrayOf(0,1,3,4), true),
            arrayOf("""a\'b""", "a\'b", intArrayOf(0,1,3,4), true),
            arrayOf("""a\tb""", "a\tb", intArrayOf(0,1,3,4), true),
            arrayOf("""a\nb""", "a\nb", intArrayOf(0,1,3,4), true),
            arrayOf("""a\rb""", "a\rb", intArrayOf(0,1,3,4), true),
            arrayOf("""a\0b""", "a\u0000b", intArrayOf(0,1,3,4), true),
            arrayOf("""a\\b""", "a\\b", intArrayOf(0,1,3,4), true),
            arrayOf("""a\x01b""", "a\u0001b", intArrayOf(0,1,5,6), true),
            arrayOf("""a\x7Fb""", "a\u007Fb", intArrayOf(0,1,5,6), true),
            // We should fail on `\x80` because it's not ASCII char, but currently we successfully parse it.
//            arrayOf("""a\x80b""", "a", intArrayOf(0,1,2), false),
            arrayOf("""a\x+1b""", "a", intArrayOf(0,1,2), false),
            arrayOf("""a\x-1b""", "a", intArrayOf(0,1,2), false),
            arrayOf("""a\u{0}b""", "a\u0000b", intArrayOf(0,1,6,7), true),
            arrayOf("""a\u{001234}b""", "a\u1234b", intArrayOf(0,1,11,12), true),
            arrayOf("""a\u{00_12__34}b""", "a\u1234b", intArrayOf(0,1,14,15), true),
            arrayOf("""a\u{00_12__34_5}b""", "a", intArrayOf(0,1,2), false),
            arrayOf("""a\u{_00}b""", "a", intArrayOf(0,1,2), false),
            arrayOf("""a\ab""", "a", intArrayOf(0,1,2), false)
        )
    }
}

package org.rust.lang.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UnescapeRustTest(private val input: String,
                       private val expected: String,
                       private val unicode: Boolean,
                       private val eol: Boolean) {
    @Test
    fun test() = assertEquals(expected, input.unescapeRust(unicode, eol))

    companion object {
        @Parameterized.Parameters(name = "{index}: \"{0}\" → \"{1}\" U:{2} E:{3}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf("aaa", "aaa", true, true),
            arrayOf("aaa", "aaa", false, false),
            arrayOf("a\\na", "a\na", true, true),
            arrayOf("a\\ra", "a\ra", true, true),
            arrayOf("a\\ta", "a\ta", true, true),
            arrayOf("a\\0a", "a\u0000a", true, true),
            arrayOf("a\\'a", "a'a", true, true),
            arrayOf("a\\\"a", "a\"a", true, true),
            arrayOf("a\\\\a", "a\\a", true, true),
            arrayOf("a\\x20a", "a a", true, true),
            arrayOf("a\\x0aa", "a\na", true, true),
            arrayOf("a\\x0Aa", "a\na", true, true),
            arrayOf("foo\\\n    bar", "foobar", true, true),
            arrayOf("foo\\\r\n    bar", "foobar", true, true),
            arrayOf("foo\\\r\n    bar", "foo\\\r\n    bar", true, false),
            arrayOf("foo\\r\\nbar", "foo\r\nbar", true, true),
            arrayOf("\\u{0119}dw\\u{0105}rd", "\u0119dw\u0105rd", true, true),
            arrayOf("\\u{0119}dw\\u{0105}rd", "\\u{0119}dw\\u{0105}rd", false, true),
            arrayOf("\\u{0}", "\u0000", true, true),
            arrayOf("\\u{00}", "\u0000", true, true),
            arrayOf("\\u{000}", "\u0000", true, true),
            arrayOf("\\u{0000}", "\u0000", true, true),
            arrayOf("\\u{00000}", "\u0000", true, true),
            arrayOf("\\u{000000}", "\u0000", true, true),
            arrayOf("\\u{0000000}", "\\u{0000000}", true, true),
            arrayOf("\\u{00000000}", "\\u{00000000}", true, true),
            arrayOf("\\u{}", "\\u{}", true, true),
            arrayOf("\\u{", "\\u{", true, true),
            arrayOf("\\u", "\\u", true, true),
            arrayOf("\\u{zzzz}", "\\u{zzzz}", true, true)
        )
    }
}

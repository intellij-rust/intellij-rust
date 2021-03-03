/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.parser.createRustPsiBuilder

class DocsLoweringTest : RsTestBase() {
    fun `test EOL doc 0 spaces`() = doTest("""
        ///foo
    """, """
        #[doc="foo"]
    """)

    fun `test EOL doc 1 space`() = doTest("""
        /// foo
    """, """
        #[doc=" foo"]
    """)

    fun `test EOL doc 2 spaces`() = doTest("""
        ///  foo
    """, """
        #[doc="  foo"]
    """)

    fun `test 2 EOL comments`() = doTest("""
        /// foo
        /// bar
    """, """
        #[doc=" foo"]
        #[doc=" bar"]
    """)

    fun `test 2 EOL comments with different indent`() = doTest("""
        /// foo
            /// bar
    """, """
        #[doc=" foo"]
        #[doc=" bar"]
    """)

    fun `test empty EOL comment`() = doTest("""
        ///
        /// foo
    """, """
        #[doc=""]
        #[doc=" foo"]
    """)

    fun `test one-line block comment`() = doTest("""
        /**foo*/
    """, """
        #[doc="foo"]
    """)

    fun `test one-line block comment with space before`() = doTest("""
        /** foo*/
    """, """
        #[doc=" foo"]
    """)

    fun `test one-line block comment with space before and after`() = doTest("""
        /** foo */
    """, """
        #[doc=" foo "]
    """)

    fun `test one-line block comment with extra asterisk`() = doTest("""
        /**foo**/
    """, """
        #[doc="foo*"]
    """)

    fun `test block comment`() = doTest("""
        /**
         * foo
         */
    """, """
        #[doc="\n * foo\n "]
    """)

    fun `test block comment with extra asterisk`() = doTest("""
        /**
         * foo
         **/
    """, """
        #[doc="\n * foo\n *"]
    """)

    fun `test block comment 2 lines`() = doTest("""
        /**
         * foo
         * bar
         */
    """, """
        #[doc="\n * foo\n * bar\n "]
    """)

    fun `test block comment with docs on the first line`() = doTest("""
        /** foo
         *  bar
         *  baz
         */
    """, """
        #[doc=" foo\n *  bar\n *  baz\n "]
    """)

    fun `test block comment 2 lines with docs on the last line`() = doTest("""
        /**
         * foo
         * bar */
    """, """
        #[doc="\n * foo\n * bar "]
    """)

    fun `test block comment without infix`() = doTest("""
        /**
         foo
         */
    """, """
        #[doc="\n foo\n "]
    """)

    fun `test quote escaping`() = doTest("""
        /// "foo"
    """, """
        #[doc=" \"foo\""]
    """)

    fun `test apostrophe escaping`() = doTest("""
        /// 'foo'
    """, """
        #[doc=" \'foo\'"]
    """)

    fun `test simple emoji is not escaped`() = doTest("""
        /// ❤
    """, """
        #[doc=" ❤"]
    """)

    // TODO We should find a way to detect Grapheme_Extend characters in Java
    fun `test character from Grapheme_Extend`() = expect<ComparisonFailure> {
    doTest("""
        /// ${0x981.toChar()}
    """, """
        #[doc=" \u{981}"]
    """)
    }

    fun `test non-printable character`() = doTest("""
        /// ${1.toChar()}
    """, """
        #[doc=" \u{1}"]
    """)

    fun `test inner EOL doc`() = doTest("""
        //! foo
    """, """
        #![doc=" foo"]
    """)

    fun `test inner block doc`() = doTest("""
        /*! foo */
    """, """
        #![doc=" foo "]
    """)

    private fun doTest(
        @Language("Rust") code: String,
        @Language("Rust", suffix = "fn foo() {}") expected: String
    ) {
        val (expanded, _) = project.createRustPsiBuilder(code.trimIndent()).lowerDocComments()
            ?: error("No doc comments in the source")
        assertEquals(expected.trimIndent() + "\n", expanded.toString())
    }
}

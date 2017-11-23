/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import org.intellij.lang.annotations.Language

class RsEnterInStringLiteralHandlerTest : RsTypingTestBase() {
    override val dataPath = "org/rust/ide/typing/string/fixtures"

    fun `test simple`() = doTest("""
        fn main() {
            let lit = "Hello, /*caret*/World";
            println!("{}", lit);
        }
    """, """
        fn main() {
            let lit = "Hello, \
            /*caret*/World";
            println!("{}", lit);
        }
    """)

    fun `test multiline text`() = doTest("""
        fn main() {
            let lit = "\
Multiline text

last/*caret*/ line";
        }
    """, """
        fn main() {
            let lit = "\
Multiline text

last
 /*caret*/line";
        }
    """)

    fun `test before opening`() = doTest("""
        fn main() {
            let lit =/*caret*/"Hello, World";
        }
    """, """
        fn main() {
            let lit =
                /*caret*/"Hello, World";
        }
    """)

    fun `test after opening`() = doTest("""
        fn main() {
            "/*caret*/Hello, World";
        }
    """, """
        fn main() {
            "\
            /*caret*/Hello, World";
        }
    """)

    fun `test inside opening`() = doTest("""
        fn main() {
            b/*caret*/"Hello, World";
        }
    """, """
        fn main() {
            b
            /*caret*/"Hello, World";
        }
    """)

    fun `test before closing`() = doTest("""
        fn main() {
            "Hello, World/*caret*/";
        }
    """, """
        fn main() {
            "Hello, World\
            /*caret*/";
        }
    """)

    fun `test after closing`() = doTest("""
        fn main() {
            "Hello, World"/*caret*/;
        }
    """, """
        fn main() {
            "Hello, World"
            /*caret*/;
        }
    """)

    fun `test raw literal`() = doTest("""
        fn main() {
            r"Hello,/*caret*/ World";
        }
    """, """
        fn main() {
            r"Hello,
            /*caret*/ World";
        }
    """)

    fun `test before escape`() = doTest("""
        fn main() {
            "foo/*caret*/\n"
        }
    """, """
        fn main() {
            "foo\
            /*caret*/\n"
        }
    """)

    fun `test after escape`() = doTest("""
        fn main() {
            "foo\n/*caret*/bar"
        }
    """, """
        fn main() {
            "foo\n\
            /*caret*/bar"
        }
    """)

    fun `test inside escape`() = doTest("""
        fn main() {
            "foo\u{00/*caret*/00}"
        }
    """, """
        fn main() {
            "foo\u{00
            /*caret*/00}"
        }
    """)

    fun `test inside escape after slash`() = doTest("""
        fn main() {
            "foo\/*caret*/u{0000}"
        }
    """, """
        fn main() {
            "foo\
            /*caret*/u{0000}"
        }
    """)

    fun `test between quote escape`() = doTest("""
        fn main() {
            "/*caret*/\n"
        }
    """, """
        fn main() {
            "\
            /*caret*/\n"
        }
    """)

    fun `test between escape quote`() = doTest("""
        fn main() {
            "foo\n/*caret*/"
        }
    """, """
        fn main() {
            "foo\n\
            /*caret*/"
        }
    """)

    fun `test incomplete`() = doTest("""
        fn main() {
            "foo/*caret*/
    """, """
        fn main() {
            "foo
                /*caret*/
    """)

    fun `test escaped eof`() = doTest("""
        fn main() {
            "foo\n/*caret*/
    """, """
        fn main() {
            "foo\n
                /*caret*/
    """)

    fun `test raw eof`() = doTest("""
        fn main() {
            r##"foo/*caret*/
    """, """
        fn main() {
            r##"foo
                /*caret*/
    """)

    fun `test incomplete escaped`() = doTest("""
        fn main() {
            "foo\n/*caret*/
        }
    """, """
        fn main() {
            "foo\n\
        /*caret*/
        }
    """)

    // FIXME: probably should not indent in this case
    fun `test incomplete raw`() = doTest("""
        fn main() {
            r##"foo/*caret*/
        }
    """, """
        fn main() {
            r##"foo
            /*caret*/
        }
    """)

    fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before.trimIndent()).withCaret()
        myFixture.type('\n')
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }
}

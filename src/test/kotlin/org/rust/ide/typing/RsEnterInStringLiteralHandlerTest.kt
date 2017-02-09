package org.rust.ide.typing

import org.intellij.lang.annotations.Language

class RsEnterInStringLiteralHandlerTest : RsTypingTestBase() {
    override val dataPath = "org/rust/ide/typing/string/fixtures"

    fun testSimple() = doTest("""
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

    fun testMultilineText() = doTest("""
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

    fun testBeforeOpening() = doTest("""
        fn main() {
            let lit =/*caret*/"Hello, World";
        }
    """, """
        fn main() {
            let lit =
                /*caret*/"Hello, World";
        }
    """)

    fun testAfterOpening() = doTest("""
        fn main() {
            "/*caret*/Hello, World";
        }
    """, """
        fn main() {
            "\
            /*caret*/Hello, World";
        }
    """)

    fun testInsideOpening() = doTest("""
        fn main() {
            b/*caret*/"Hello, World";
        }
    """, """
        fn main() {
            b
            /*caret*/"Hello, World";
        }
    """)

    fun testBeforeClosing() = doTest("""
        fn main() {
            "Hello, World/*caret*/";
        }
    """, """
        fn main() {
            "Hello, World\
            /*caret*/";
        }
    """)

    fun testAfterClosing() = doTest("""
        fn main() {
            "Hello, World"/*caret*/;
        }
    """, """
        fn main() {
            "Hello, World"
            /*caret*/;
        }
    """)

    fun testRawLiteral() = doTest("""
        fn main() {
            r"Hello,/*caret*/ World";
        }
    """, """
        fn main() {
            r"Hello,
            /*caret*/ World";
        }
    """)

    fun testBeforeEscape() = doTest("""
        fn main() {
            "foo/*caret*/\n"
        }
    """, """
        fn main() {
            "foo\
            /*caret*/\n"
        }
    """)

    fun testAfterEscape() = doTest("""
        fn main() {
            "foo\n/*caret*/bar"
        }
    """, """
        fn main() {
            "foo\n\
            /*caret*/bar"
        }
    """)

    fun testInsideEscape() = doTest("""
        fn main() {
            "foo\u{00/*caret*/00}"
        }
    """, """
        fn main() {
            "foo\u{00
            /*caret*/00}"
        }
    """)

    fun testInsideEscapeAfterSlash() = doTest("""
        fn main() {
            "foo\/*caret*/u{0000}"
        }
    """, """
        fn main() {
            "foo\
            /*caret*/u{0000}"
        }
    """)

    fun testBetweenQuoteEscape() = doTest("""
        fn main() {
            "/*caret*/\n"
        }
    """, """
        fn main() {
            "\
            /*caret*/\n"
        }
    """)

    fun testBetweenEscapeQuote() = doTest("""
        fn main() {
            "foo\n/*caret*/"
        }
    """, """
        fn main() {
            "foo\n\
            /*caret*/"
        }
    """)

    fun testIncomplete() = doTest("""
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

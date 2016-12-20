package org.rust.ide.typing

class RustRawLiteralHashesBalancerTest : RustTypingTestCaseBase() {
    override val dataPath = "org/rust/ide/typing/rawHashes/fixtures"

    fun testSimpleOpen() = doTest("""
        fn main() {
            r#<caret>#"aaa"##
        }
    """, """
        fn main() {
            r##<caret>#"aaa"###
        }
    """)

    fun testSimpleClose() = doTest("""
        fn main() {
            r##"aaa"#<caret>#
        }
    """, """
        fn main() {
            r###"aaa"##<caret>#
        }
    """)

    fun testBeforeR() = doTest("""
        fn main() {
            <caret>r##"aaa"##
        }
    """, """
        fn main() {
            #<caret>r##"aaa"##
        }
    """)

    fun testAfterR() = doTest("""
        fn main() {
            r<caret>##"aaa"##
        }
    """, """
        fn main() {
            r#<caret>##"aaa"###
        }
    """)

    fun testBeforeQuote() = doTest("""
        fn main() {
            r##<caret>"aaa"##
        }
    """, """
        fn main() {
            r###<caret>"aaa"###
        }
    """)

    fun testAfterQuote() = doTest("""
        fn main() {
            r##"aaa"<caret>##
        }
    """, """
        fn main() {
            r###"aaa"#<caret>##
        }
    """)

    fun testAtEnd() = doTest("""
        fn main() {
            r##"aaa"##<caret>
        }
    """, """
        fn main() {
            r###"aaa"###<caret>
        }
    """)

    fun testAtTotalEnd() = doTest("""
        // Caret at EOL is important here!
        fn main() {
            r##"aaa"##<caret>""", """
        // Caret at EOL is important here!
        fn main() {
            r###"aaa"###<caret>"""
    )

    fun testInsideValue() = doTest("""
        fn main() {
            r##"<caret>"##
        }
    """, """
        fn main() {
            r##"#<caret>"##
        }
    """)

    fun testNoQuotes() = doTest("""
        fn main() {
            r<caret>"aaa"
        }
    """, """
        fn main() {
            r#<caret>"aaa"#
        }
    """)


    fun testNoQuotes2() = doTest("""
        fn main() {
            r"aaa"<caret>
        }
    """, """
        fn main() {
            r#"aaa"#<caret>
        }
    """)

    fun testMulticursor() = doTest("""
        fn main() {
            r"aa"<caret>;
            r##"aa"#<caret>#;
            r##<caret>##"aaa"####;
        }
    """, """
        fn main() {
            r#"aa"#<caret>;
            r###"aa"##<caret>#;
            r###<caret>##"aaa"#####;
        }
    """)

    fun testByteLiteral() = doTest("""
        fn main() {
            br#<caret>#"aaa"##
        }
    """, """
        fn main() {
            br##<caret>#"aaa"###
        }
    """)

    private fun doTest(before: String, after: String) {
        // First type a pound sign...
        doTestByText(before, after, '#')
        // ...then delete it.
        doTestByText(after, before, '\b')
    }
}

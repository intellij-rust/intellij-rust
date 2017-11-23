/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

class RsRawLiteralHashesBalancerTest : RsTypingTestBase() {
    override val dataPath = "org/rust/ide/typing/rawHashes/fixtures"

    fun `test simple open`() = doTest("""
        fn main() {
            r#<caret>#"aaa"##
        }
    """, """
        fn main() {
            r##<caret>#"aaa"###
        }
    """)

    fun `test simple close`() = doTest("""
        fn main() {
            r##"aaa"#<caret>#
        }
    """, """
        fn main() {
            r###"aaa"##<caret>#
        }
    """)

    fun `test before r`() = doTest("""
        fn main() {
            <caret>r##"aaa"##
        }
    """, """
        fn main() {
            #<caret>r##"aaa"##
        }
    """)

    fun `test after r`() = doTest("""
        fn main() {
            r<caret>##"aaa"##
        }
    """, """
        fn main() {
            r#<caret>##"aaa"###
        }
    """)

    fun `test before quote`() = doTest("""
        fn main() {
            r##<caret>"aaa"##
        }
    """, """
        fn main() {
            r###<caret>"aaa"###
        }
    """)

    fun `test after quote`() = doTest("""
        fn main() {
            r##"aaa"<caret>##
        }
    """, """
        fn main() {
            r###"aaa"#<caret>##
        }
    """)

    fun `test at end`() = doTest("""
        fn main() {
            r##"aaa"##<caret>
        }
    """, """
        fn main() {
            r###"aaa"###<caret>
        }
    """)

    fun `test at total end`() = doTest("""
        // Caret at EOL is important here!
        fn main() {
            r##"aaa"##<caret>""", """
        // Caret at EOL is important here!
        fn main() {
            r###"aaa"###<caret>"""
    )

    fun `test inside value`() = doTest("""
        fn main() {
            r##"<caret>"##
        }
    """, """
        fn main() {
            r##"#<caret>"##
        }
    """)

    fun `test no quotes`() = doTest("""
        fn main() {
            r<caret>"aaa"
        }
    """, """
        fn main() {
            r#<caret>"aaa"#
        }
    """)


    fun `test no quotes 2`() = doTest("""
        fn main() {
            r"aaa"<caret>
        }
    """, """
        fn main() {
            r#"aaa"#<caret>
        }
    """)

    fun `test multicursor`() = doTest("""
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

    fun `test byte literal`() = doTest("""
        fn main() {
            br#<caret>#"aaa"##
        }
    """, """
        fn main() {
            br##<caret>#"aaa"###
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/817
    fun `test don't messup broken literal`() = doTestByText("""
        static CHILD_TEMPLATE: &'static str = r<caret>"
        {% extends "parent.html" %}
        ";
    """, """
        static CHILD_TEMPLATE: &'static str = r#<caret>"
        {% extends "parent.html" %}
        ";
    """, '#')

    private fun doTest(before: String, after: String) {
        // First type a pound sign...
        doTestByText(before, after, '#')
        // ...then delete it.
        doTestByText(after, before, '\b')
    }
}

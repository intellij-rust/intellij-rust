/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

class RsSelectionHandlerTest : RsSelectionHandlerTestBase() {
    fun `test string literal`() = doTest("""
        const C: &str = "foo <caret>bar baz";
    """, """
        const C: &str = "foo <selection><caret>bar</selection> baz";
    """, """
        const C: &str = "<selection>foo <caret>bar baz</selection>";
    """, """
        const C: &str = <selection>"foo <caret>bar baz"</selection>;
    """)

    fun `test string literal escape code`() = doTest("""
        const C: &str = "foo <caret>\u{00} baz";
    """, """
        const C: &str = "foo <selection><caret>\u{00}</selection> baz";
    """)

    fun `test raw string literal`() = doTest("""
        const C: &str = r#"foo <caret>bar baz"#;
    """, """
        const C: &str = r#"foo <selection><caret>bar</selection> baz"#;
    """, """
        const C: &str = r#"<selection>foo <caret>bar baz</selection>"#;
    """, """
        const C: &str = <selection>r#"foo <caret>bar baz"#</selection>;
    """)

    fun `test byte string literal`() = doTest("""
        const C: &[u8] = b"foo <caret>bar baz";
    """, """
        const C: &[u8] = b"foo <selection><caret>bar</selection> baz";
    """, """
        const C: &[u8] = b"<selection>foo <caret>bar baz</selection>";
    """, """
        const C: &[u8] = <selection>b"foo <caret>bar baz"</selection>;
    """)
}

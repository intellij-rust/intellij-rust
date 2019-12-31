/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

class RsStringLiteralSelectionHandlerTest : RsSelectionHandlerTestBase() {

    fun `test select whole string literal value`() = doTest("""
        const C: &str = "st<caret>ring literal";
    """, """
        const C: &str = "<selection>string</selection> literal";
    """, """
        const C: &str = "<selection>string literal</selection>";
    """, """
        const C: &str = <selection>"string literal"</selection>;
    """)

    fun `test select string literal escape symbols`() = doTest("""
        const C: &str = "foo <caret>\u{2D} baz";
    """, """
        const C: &str = "foo <selection><caret>\u{2D}</selection> baz";
    """)

    fun `test select whole raw string literal value`() = doTest("""
        const C: &str = r#"raw <caret>string literal"#;
    """, """
        const C: &str = r#"raw <selection><caret>string</selection> literal"#;
    """, """
        const C: &str = r#"<selection>raw <caret>string literal</selection>"#;
    """, """
        const C: &str = <selection>r#"raw <caret>string literal"#</selection>;
    """)

    fun `test select whole byte string literal value`() = doTest("""
        const C: &[u8] = b"byte st<caret>ring literal";
    """, """
        const C: &[u8] = b"byte <selection>string</selection> literal";
    """, """
        const C: &[u8] = b"<selection>byte string literal</selection>";
    """, """
        const C: &[u8] = <selection>b"byte string literal"</selection>;
    """)
}

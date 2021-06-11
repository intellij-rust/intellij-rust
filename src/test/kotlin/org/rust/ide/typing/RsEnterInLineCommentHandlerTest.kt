/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.injected.editor.VirtualFileWindow
import org.intellij.lang.annotations.Language

class RsEnterInLineCommentHandlerTest : RsTypingTestBase() {
    override val dataPath = "org/rust/ide/typing/lineComment/fixtures"

    fun `test before line comment`() = doTest()

    fun `test in line comment`() = doTestByText("""
        fn double(x: i32) -> i32 {
            // multi<caret>ply by two
            x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            // multi
            // <caret>ply by two
            x * 2
        }
    """)

    fun `test after line comment`() = doTestByText("""
        fn double(x: i32) -> i32 {
            // multiply by two<caret>
            x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            // multiply by two
            <caret>
            x * 2
        }
    """)

    fun `test in block comment`() = doTestByText("""
        fn double(x: i32) -> i32 {
            /* multi<caret>ply by two */
            x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            /* multi
            <caret>ply by two */
            x * 2
        }
    """)

    fun `test in outer doc comment`() = doTestByText("""
        /// multi<caret>ply by two
        fn double(x: i32) -> i32 {
            x * 2
        }
    """, """
        /// multi
        /// <caret>ply by two
        fn double(x: i32) -> i32 {
            x * 2
        }
    """)

    fun `test after outer doc comment`() = doTest()

    fun `test in inner doc comment`() = doTestByText("""
        fn double(x: i32) -> i32 {
            //! multi<caret>ply by two
            x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            //! multi
            //! <caret>ply by two
            x * 2
        }
    """)

    fun `test after inner doc comment`() = doTestByText("""
        fn double(x: i32) -> i32 {
            //! multiply by two<caret>
            x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            //! multiply by two
            //! <caret>
            x * 2
        }
    """)

    fun `test after module comment`() = doTestByText("""
        //! Awesome module
        //! Does stuff!


        <caret>
        fn undocumented_fn() {}
    """, """
        //! Awesome module
        //! Does stuff!




        fn undocumented_fn() {}
    """)

    fun `test directly after token`() = doTestByText("""
        fn double(x: i32) -> i32 {
            //<caret>multiply by two
            x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            //
            // <caret>multiply by two
            x * 2
        }
    """)

    fun `test inside token`() = doTestByText("""
        fn double(x: i32) -> i32 {
            //<caret>! multiply by two
            x * 2
        }
    """, """
        fn double(x: i32) -> i32 {
            //
            <caret>! multiply by two
            x * 2
        }
    """)

    fun `test inside comment directly before next token`() = doTest()

    fun `test inside comment inside token`() = doTestByText("""
        /// foo //<caret>/ bar
    """, """
        /// foo //
        /// <caret>/ bar
    """)

    fun `test at file beginning`() = doTestByText("""
        <caret>
        // Some comment
    """, """

        <caret>
        // Some comment
    """)

    fun `test inside string literal`() = doTestByText("""
        // Taken from issue #185

        fn read_manifest_output() -> String {
            "\
        {\
            \"name\":\"foo\",\
            \"version\":\"0.5.0\",\
            \"dependencies\":[],\
            \"targets\":[{\
                \"kind\":[\"bin\"],\
                \"name\":\"foo\",\
                \"src_path\":\"src[..]foo.rs\"\
            }],\<caret>
            \"manifest_path\":\"[..]Cargo.toml\"\
        }".into()
        }
    """, """
        // Taken from issue #185

        fn read_manifest_output() -> String {
            "\
        {\
            \"name\":\"foo\",\
            \"version\":\"0.5.0\",\
            \"dependencies\":[],\
            \"targets\":[{\
                \"kind\":[\"bin\"],\
                \"name\":\"foo\",\
                \"src_path\":\"src[..]foo.rs\"\
            }],\
        <caret>
            \"manifest_path\":\"[..]Cargo.toml\"\
        }".into()
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/578
    fun `test issue578`() = doTestByText("""
        //! This crate does something useful
        <caret>//! Description goes here
        //! And some notes
    """, """
        //! This crate does something useful

        <caret>//! Description goes here
        //! And some notes
    """)

    /** This also a test for [org.rust.ide.actions.RsEnterHandler] */
    fun `test in doctest injection`() = doDoctestTestByText("""
        //! ```
        //! let a =<caret>1;
        //! ```
    """, """
        //! ```
        //! let a =
        //! <caret>1;
        //! ```
    """)

    private fun doDoctestTestByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        doTestByText(before, after, fileName = "lib.rs")
        check(myFixture.file.virtualFile is VirtualFileWindow) {
            "No doctest injection found"
        }
    }
}

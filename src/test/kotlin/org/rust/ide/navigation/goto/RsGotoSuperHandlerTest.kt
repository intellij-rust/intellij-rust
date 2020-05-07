/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.lang.CodeInsightActions
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.RsLanguage

class RsGotoSuperHandlerTest : RsTestBase() {
    fun `test module from function`() = checkNavigation("""
        mod /*caret_after*/foo {
            mod bar {
                fn foo() { /*caret_before*/ }
            }
        }
    """)

    fun `test method declaration from impl`() = checkNavigation("""
        trait T {
            fn /*caret_after*/foo(); // <- should go here
        }
        impl T for () {
            fn foo/*caret_before*/() {}
        }
    """)

    fun `test constant declaration from impl`() = checkNavigation("""
        trait T {
            const /*caret_after*/Z: u32; // <- should go here
        }
        impl T for () {
            const /*caret_before*/Z: u32 = 1;
        }
    """)

    fun `test type alias declaration from impl`() = checkNavigation("""
        trait T {
             type /*caret_after*/Z; // <- should go here
        }
        impl T for () {
            type /*caret_before*/Z = ();
        }
    """)

    fun `test module from method definition`() = checkNavigation("""
        mod /*caret_after*/foo {
            mod bar {
                struct S;
                impl S { fn foo(&self) { /*caret_before*/} }
            }
        }
    """)

    fun `test module from method in trait`() = checkNavigation("""
        mod /*caret_after*/foo {
            mod bar {
                trait T { fn foo(&self) { /*caret_before*/} }
            }
        }
    """)

    fun `test on file level`() = checkNavigationInFiles("""
        //- foo.rs
        /*caret*/    // only comment

        //- main.rs
            mod foo;
        """, expected = "mod foo;")

    fun `test with path attribute`() = checkNavigationInFiles("""
        //- foo.rs
        /*caret*/    // only comment

        //- main.rs
            #[path="foo.rs"]
            mod bar;

    """, expected = """
        #[path="foo.rs"]
        mod bar;
    """)

    fun `test with path attribute in module`() = checkNavigationInFiles("""
        //- foo/qqq.rs
        /*caret*/    // only comment

        //- main.rs
        mod foo {
            #[path="qqq.rs"]
            mod bar;
        }
    """, expected = """
        #[path="qqq.rs"]
            mod bar;
    """)

    // Navigation from a crate root to Cargo.toml is tested in `CargoTomlGotoSuperHandlerTest`

    private fun checkNavigationInFiles(@Language("Rust") fileTreeText: String, expected: String) {
        checkMultiNavigationInFiles(fileTreeText, expected)
    }

    private fun checkMultiNavigationInFiles(@Language("Rust") fileTreeText: String, vararg expected: String) {
        fileTreeFromText(fileTreeText).createAndOpenFileWithCaretMarker()
        val target = gotoSuperTarget(myFixture.file)
        assertEquals(expected.toList().map { it.trimIndent() }.sorted(), target.map { it.text }.sorted())
    }

    private fun checkNavigation(@Language("Rust") code: String) {
        InlineFile(code.replace("/*caret_before*/", "<caret>").replace("/*caret_after*/", ""))
        val handler = CodeInsightActions.GOTO_SUPER.forLanguage(RsLanguage)
            ?: error("GotoSuperHandler for Rust was not found.")
        handler.invoke(project, myFixture.editor, myFixture.file)
        myFixture.checkResult(code.replace("/*caret_after*/", "<caret>").replace("/*caret_before*/", ""))
    }
}

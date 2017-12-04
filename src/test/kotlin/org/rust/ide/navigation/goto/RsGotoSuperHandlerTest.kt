/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.lang.CodeInsightActions
import junit.framework.Assert
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.RsLanguage
import org.rust.lang.RsTestBase

class RsGotoSuperHandlerTest : RsTestBase() {
    fun `test module from function`() = checkNavigation("""
        mod foo {
            mod bar {
                fn foo() { /*caret*/ }
            }
        }
    """, """
        mod /*caret*/foo {
            mod bar {
                fn foo() {  }
            }
        }
    """)

    fun `test method declataion from impl`() = checkNavigation("""
        trait T {
             fn foo(); // <- should go here
        }
        impl T for () {
            fn foo/*caret*/() {}
        }
    """, """
        trait T {
             fn /*caret*/foo(); // <- should go here
        }
        impl T for () {
            fn foo() {}
        }
    """)

    fun `test module from method definition`() = checkNavigation("""
        mod foo {
            mod bar {
                struct S;
                impl S { fn foo(&self) { /*caret*/} }
            }
        }
    """, """
        mod /*caret*/foo {
            mod bar {
                struct S;
                impl S { fn foo(&self) { } }
            }
        }
    """)

    fun `test module from method in trait`() = checkNavigation("""
        mod foo {
            mod bar {
                trait T { fn foo(&self) { /*caret*/} }
            }
        }
    """, """
        mod /*caret*/foo {
            mod bar {
                trait T { fn foo(&self) { } }
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

    private fun checkNavigationInFiles(@Language("Rust") fileTreeText: String, expected: String) {
        fileTreeFromText(fileTreeText).createAndOpenFileWithCaretMarker()
        val target = gotoSuperTarget(myFixture.file)
        assertEquals(expected.trimIndent(), target?.text)
    }

    private fun checkNavigation(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before)
        val handler = CodeInsightActions.GOTO_SUPER.forLanguage(RsLanguage)
            ?: error("GotoSuperHandler for Rust was not found.")
        handler.invoke(project, myFixture.editor, myFixture.file)
        myFixture.checkResult(replaceCaretMarker(after))
    }
}

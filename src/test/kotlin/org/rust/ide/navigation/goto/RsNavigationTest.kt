package org.rust.ide.navigation.goto

import com.intellij.lang.CodeInsightActions
import org.intellij.lang.annotations.Language
import org.rust.lang.RustLanguage
import org.rust.lang.RsTestBase

class RsNavigationTest : RsTestBase() {
    override val dataPath = ""

    fun testGotoSuper() = checkNavigation("""
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

    fun testGotoSuperMethod() = checkNavigation("""
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

    private fun checkNavigation(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before)
        val handler = CodeInsightActions.GOTO_SUPER.forLanguage(RustLanguage)
        assertNotNull("GotoSuperHandler for Rust was not found.", handler)
        handler.invoke(project, myFixture.editor, myFixture.file)
        myFixture.checkResult(replaceCaretMarker(after))
    }
}

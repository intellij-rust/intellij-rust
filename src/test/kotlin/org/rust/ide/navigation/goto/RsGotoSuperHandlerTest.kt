package org.rust.ide.navigation.goto

import com.intellij.lang.CodeInsightActions
import org.intellij.lang.annotations.Language
import org.rust.lang.RsLanguage
import org.rust.lang.RsTestBase

class RsGotoSuperHandlerTest : RsTestBase() {
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

    fun testOnFileLevel() {
        val files = ProjectFile.parseFileCollection("""
            //- foo.rs
                // only comment

            //- main.rs
                mod foo;
        """)

        for ((path, text) in files) {
            myFixture.tempDirFixture.createFile(path, text)
        }
        myFixture.configureFromTempProjectFile(files[0].path)

        val target = gotoSuperTarget(myFixture.file)
        check(target?.text == "mod foo;")
    }

    private fun checkNavigation(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before)
        val handler = CodeInsightActions.GOTO_SUPER.forLanguage(RsLanguage)
            ?: error("GotoSuperHandler for Rust was not found.")
        handler.invoke(project, myFixture.editor, myFixture.file)
        myFixture.checkResult(replaceCaretMarker(after))
    }
}

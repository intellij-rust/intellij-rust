package org.rust.ide.navigation.goto

import com.intellij.lang.CodeInsightActions
import org.rust.lang.RustLanguage
import org.rust.lang.RustTestCaseBase

class RustNavigationTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/navigation/fixtures"

    fun testGotoSuper() = checkByFile {
        val handler = CodeInsightActions.GOTO_SUPER.forLanguage(RustLanguage)
        assertNotNull("GotoSuperHandler for Rust was not found.", handler)

        handler.invoke(project, myFixture.editor, myFixture.file)
    }
}

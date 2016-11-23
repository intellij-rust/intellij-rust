package org.rust.lang.refactoring

import org.rust.lang.RustTestCaseBase

class IntroduceVariableTest : RustTestCaseBase() {
    override val dataPath = "org/rust/lang/refactoring/fixtures/introduce_variable/"


    fun testVariable() = checkByFile {
        val rustLocalVariableHandler = RustLocalVariableHandler()
        openFileInEditor("variable.rs")
        rustLocalVariableHandler.invoke(myFixture.project, myFixture.editor, myFixture.file, com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT)
    }
}

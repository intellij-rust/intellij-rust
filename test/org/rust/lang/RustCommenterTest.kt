package org.rust.lang

import com.intellij.openapi.actionSystem.IdeActions

class RustCommenterTest : RustTestCase() {
    override fun getTestDataPath() = "testData/commenter"

    private fun doTest(actionId: String) {
        myFixture.configureByFile(fileName)
        myFixture.performEditorAction(actionId)
        myFixture.checkResultByFile(fileName.replace(".rs", "_after.rs"), true)
    }

    fun testSingleLine() = doTest(IdeActions.ACTION_COMMENT_LINE)
    fun testMultiLine() = doTest(IdeActions.ACTION_COMMENT_LINE)
    fun testSingleLineBlock() = doTest(IdeActions.ACTION_COMMENT_BLOCK)
    fun testMultiLineBlock() = doTest(IdeActions.ACTION_COMMENT_BLOCK)
}

package org.rust.ide.template

import com.intellij.openapi.actionSystem.IdeActions
import org.rust.lang.RustTestCaseBase

class RustLiveTemplatesTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/template/fixtures"

    private fun expandTemplate() {
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
    }

    private fun doTest() = checkByFile { expandTemplate() }

    fun testStructField() = doTest()
}

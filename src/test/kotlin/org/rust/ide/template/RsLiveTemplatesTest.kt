package org.rust.ide.template

import com.intellij.openapi.actionSystem.IdeActions
import org.rust.lang.RsTestBase

class RsLiveTemplatesTest : RsTestBase() {
    override val dataPath = "org/rust/ide/template/fixtures"

    fun testStructField() = expandAndCompare()
    fun testPrint() = expandAndCompare()

    fun testAttribute() = noSnippetApplicable()
    fun testComment() = noSnippetApplicable()
    fun testDocComment() = noSnippetApplicable()
    fun testStringLiteral() = noSnippetApplicable()
    fun testRawStringLiteral() = noSnippetApplicable()
    fun testByteStringLiteral() = noSnippetApplicable()

    private fun expandAndCompare() = checkByFile { expandTemplate() }
    private fun noSnippetApplicable() = checkByFile { expandTemplate() }

    private fun expandTemplate() {
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
    }
}

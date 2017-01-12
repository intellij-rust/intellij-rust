package org.rust.ide.intentions

import org.rust.lang.RsTestBase

class ExtractInlineModuleIntentionTest : RsTestBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun testValidExtractInlineModule() = extractInlineModule()

    fun testInvalidExtractInlineModule() = extractInlineModule()

    private fun extractInlineModule() = checkByDirectory {
        openFileInEditor("main.rs")
        myFixture.launchAction(ExtractInlineModuleIntention())
    }
}

package org.rust.ide.intentions

import org.rust.cargo.CargoProjectDescription
import org.rust.lang.core.resolve.RustMultiFileResolveTestCaseBase

class ExtractInlineModuleIntentionTest : RustMultiFileResolveTestCaseBase() {
    override val targets: Collection<CargoProjectDescription.Target> = listOf(binTarget("main.rs"))
    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun testValidExtractInlineModule() = extractInlineModule()

    fun testInvalidExtractInlineModule() = extractInlineModule()

    private fun extractInlineModule() = checkByDirectory {
        openFileInEditor("main.rs")
        myFixture.launchAction(ExtractInlineModule())
    }
}

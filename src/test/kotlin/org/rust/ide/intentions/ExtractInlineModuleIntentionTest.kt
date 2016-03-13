package org.rust.ide.intentions

import com.intellij.testFramework.PlatformTestUtil
import org.rust.cargo.CargoProjectDescription
import org.rust.lang.core.resolve.RustMultiFileResolveTestCaseBase

class ExtractInlineModuleIntentionTest : RustMultiFileResolveTestCaseBase() {
    override val targets: Collection<CargoProjectDescription.Target> = listOf(binTarget("main.rs"))
    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun testValidExtractInlineModule() {
        extractInlineModule()
    }

    fun testInvalidExtractInlineModule() {
        extractInlineModule()
    }

    private fun extractInlineModule() {
        val before = "$testName/before"
        val after = "$testName/after"
        val beforeDir = myFixture.copyDirectoryToProject(before, "")
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("main.rs"))

        myFixture.launchAction(ExtractInlineModule())

        val afterDir = getVirtualFileByName(testDataPath + after)
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }
}

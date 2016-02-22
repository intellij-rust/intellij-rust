package org.rust.ide.intentions

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import org.rust.lang.RustTestCaseBase

class RustIntentionsTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun testExpandModule() {
        val before = "$testName/before"
        val after = "$testName/after"
        val beforeDir = myFixture.copyDirectoryToProject(before, "")
        myFixture.openFileInEditor(myFixture.findFileInTempDir("foo.rs"))

        myFixture.launchAction(ExpandModule())

        val afterDir = getVirtualFileByName(testDataPath + after)
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    fun testContractModule() {
        val before = "$testName/before"
        val after = "$testName/after"
        val beforeDir = myFixture.copyDirectoryToProject(before, "")
        myFixture.openFileInEditor(myFixture.findFileInTempDir("other/mod.rs"))

        myFixture.launchAction(ContractModule())

        val afterDir = getVirtualFileByName(testDataPath + after)
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    private fun getVirtualFileByName(path: String): VirtualFile? =
            LocalFileSystem.getInstance().findFileByPath(path)
}

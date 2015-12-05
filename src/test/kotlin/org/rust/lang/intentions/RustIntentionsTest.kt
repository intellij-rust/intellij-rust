package org.rust.lang.intentions

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import org.rust.lang.RustTestCase

class RustIntentionsTest : RustTestCase() {
    override fun getTestDataPath() = "src/test/resources/org/rust/lang/intentions/fixtures/"

    fun testExpandModule() {
        val before = testName + "/before"
        val after = testName + "/after"
        val beforeDir = myFixture.copyDirectoryToProject(before, "")
        myFixture.openFileInEditor(myFixture.findFileInTempDir("foo.rs"))

        myFixture.launchAction(ExpandModule())

        val afterDir = getVirtualFileByName(testDataPath + after)
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    private fun getVirtualFileByName(path: String): VirtualFile? =
            LocalFileSystem.getInstance().findFileByPath(path)
}

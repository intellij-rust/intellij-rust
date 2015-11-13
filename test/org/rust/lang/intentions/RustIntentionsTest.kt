package org.rust.lang.intentions

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import org.assertj.core.api.Assertions
import org.rust.lang.RustTestCase
import java.io.File

class RustIntentionsTest : RustTestCase() {
    override fun getTestDataPath() = "testData/org/rust/lang/intentions/fixtures/"

    fun testExpandModule() {
        val originalVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(File("$testDataPath/$fileName"))
        myFixture.configureByFile(fileName)
        myFixture.launchAction(ExpandModule())

        Assertions.assertThat(myFixture.file.virtualFile.path).isEqualTo("/src/expand_module/mod.rs")
        PlatformTestUtil.assertFilesEqual(myFixture.file.virtualFile, originalVirtualFile)
    }
}

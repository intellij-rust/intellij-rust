package org.rust.lang.structure

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil.assertTreeEqual
import org.rust.lang.RustTestCase
import java.io.File

class RustStructureViewTest : RustTestCase() {
    override fun getTestDataPath() = "testData/structure"

    private fun doTest(expected: String) {
        myFixture.configureByFile(fileName);
        myFixture.testStructureView { assertTreeEqual(it.tree, expected) }
    }

    private fun doFileTest() {
        val text = FileUtil.loadFile(File(testDataPath + "/" + fileName.replace(".rs", ".txt")))
        doTest(text)
    }

    //@formatter:off
    fun testFunctions() { doFileTest() }
    fun testStructs() { doFileTest() }
    fun testEnums() { doFileTest() }
    //@formatter:on
}

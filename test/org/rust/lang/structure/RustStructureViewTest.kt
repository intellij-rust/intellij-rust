package org.rust.lang.structure

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil.assertTreeEqual
import com.intellij.util.ui.tree.TreeUtil
import org.rust.lang.RustTestCase
import java.io.File

class RustStructureViewTest : RustTestCase() {
    override fun getTestDataPath() = "testData/structure"

    private fun doTest(expected: String) {
        myFixture.configureByFile(fileName);
        myFixture.testStructureView {
            TreeUtil.expandAll(it.tree)
            assertTreeEqual(it.tree, expected)
        }
    }

    private fun doFileTest() {
        val text = FileUtil.loadFile(File(testDataPath + "/" + fileName.replace(".rs", ".txt")))
        doTest(text)
    }

    fun testFunctions() = doFileTest()
    fun testStructs()   = doFileTest()
    fun testEnums()     = doFileTest()
    fun testTraits()    = doFileTest()
    fun testImpls()     = doFileTest()
    fun testMods()      = doFileTest()
}

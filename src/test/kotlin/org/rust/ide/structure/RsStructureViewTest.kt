package org.rust.ide.structure

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil.assertTreeEqual
import com.intellij.util.ui.tree.TreeUtil
import org.rust.lang.RsTestBase
import java.io.File

class RsStructureViewTest : RsTestBase() {

    override val dataPath = "org/rust/ide/structure/fixtures"

    fun testFunctions() = doFileTest()

    fun testConsts() = doFileTest()
    fun testEnums() = doFileTest()
    fun testImpls() = doFileTest()
    fun testMods() = doFileTest()
    fun testStructs() = doFileTest()
    fun testStatics() = doFileTest()
    fun testTraits() = doFileTest()
    fun testTypeAliases() = doFileTest()

    private fun doTest(expected: String) {
        myFixture.configureByFile(fileName)
        myFixture.testStructureView {
            TreeUtil.expandAll(it.tree)
            assertTreeEqual(it.tree, expected)
        }
    }

    private fun doFileTest() {
        val file = File(testDataPath + "/" + fileName.replace(".rs", ".txt"))
        val expected = FileUtil.loadFile(file, /* convertLineSeparators = */ true)
        doTest(expected)
    }
}

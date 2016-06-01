package org.rust.ide.folding

import org.rust.lang.RustTestCaseBase

class RustFoldingTestCase : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/folding/fixtures"

    fun testFn() = doTest()
    fun testBlockExpr() = doTest()
    fun testImpl() = doTest()
    fun testImplMethod() = doTest()
    fun testStruct() = doTest()
    fun testStructExpr() = doTest()
    fun testTrait() = doTest()
    fun testTraitMethod() = doTest()
    fun testEnum() = doTest()
    fun testEnumVariant() = doTest()
    fun testMod() = doTest()

    private fun doTest() {
        myFixture.testFolding("$testDataPath/$fileName")
    }
}

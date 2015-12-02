package org.rust.lang.core.resolve

class RustUseResolveTestCase : RustResolveTestCaseBase() {
    override fun getTestDataPath() = "testData/org/rust/lang/core/resolve/fixtures/use"

    fun testViewPath() = checkIsBound()
    fun testUsePath() = checkIsBound()
    fun testChildFromParent() = checkIsBound(atOffset = 117)
    fun testPathRename() = checkIsBound(atOffset = 3)
    fun testNoUse() = checkIsUnbound()
    fun testCycle() = checkIsUnbound()
}

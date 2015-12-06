package org.rust.lang.core.resolve

class RustUseResolveTestCase : RustResolveTestCaseBase() {
    override fun getTestDataPath() = super.getTestDataPath() + "/use"

    fun testViewPath() = checkIsBound()
    fun testUsePath() = checkIsBound()
    fun testChildFromParent() = checkIsBound(atOffset = 117)
    fun testPathRename() = checkIsBound(atOffset = 3)
    fun testDeepRedirection() = checkIsBound(atOffset = 21)
    fun testRelativeChild() = checkIsBound()
    fun testNoUse() = checkIsUnbound()
    fun testCycle() = checkIsUnbound()
}

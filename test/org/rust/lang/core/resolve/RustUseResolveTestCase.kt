package org.rust.lang.core.resolve

class RustUseResolveTestCase : RustResolveTestCaseBase() {
    override fun getTestDataPath() = "testData/org/rust/lang/core/resolve/fixtures/use"

    fun testViewPath() = checkIsBound()
}

package org.rust.lang.core.resolve

class RustTypeResolveTestCase : RustResolveTestCaseBase() {
    override val dataPath = "org/rust/lang/core/resolve/fixtures/type"

    fun testMethod() = checkIsBound(atOffset = 27)
}

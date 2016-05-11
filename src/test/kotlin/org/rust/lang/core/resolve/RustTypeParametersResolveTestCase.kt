package org.rust.lang.core.resolve

class RustTypeParametersResolveTestCase : RustResolveTestCaseBase() {
    override val dataPath = "org/rust/lang/core/resolve/fixtures/type_parameters"

    fun testFn() = checkIsBound()
    fun testImplMethod() = checkIsBound()
    fun testTraitMethod() = checkIsBound()
}

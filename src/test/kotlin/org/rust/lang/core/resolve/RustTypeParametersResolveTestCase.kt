package org.rust.lang.core.resolve

class RustTypeParametersResolveTestCase : RustResolveTestCaseBase() {
    override val dataPath = "org/rust/lang/core/resolve/fixtures/type_parameters"

    fun testFn() = checkIsBound()
    fun testImplMethod() = checkIsBound()
    fun testTraitMethod() = checkIsBound()
    fun testStruct() = checkIsBound()
    fun testEnum() = checkIsBound()
    fun testTrait() = checkIsBound()
    fun testImpl() = checkIsBound(atOffset = 31)
    fun testTypeAlias() = checkIsBound()

    fun testNoLeakInEnum() = checkIsUnbound()
    fun testNoLeakInStruct() = checkIsUnbound()
}

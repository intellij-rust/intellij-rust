package org.rust.lang.core.resolve

class RustTypeAwareResolveTestCase : RustResolveTestCaseBase() {
    override val dataPath = "org/rust/lang/core/resolve/fixtures/type_aware"

    fun testSelfMethodCallExpr() = checkIsBound(atOffset = 27)

    fun testMethodCallExpr1() = checkIsBound(atOffset = 27)
    fun testMethodCallExpr2() = checkIsBound(atOffset = 30)

    fun testSelfFieldExpr() = checkIsBound()
    fun testFieldExpr() = checkIsBound()
    fun testNestedFieldExpr() = checkIsBound()

    fun testLetDeclCallExpr() = checkIsBound()
    fun testLetDeclMethodCallExpr() = checkIsBound()
    fun testLetDeclPatIdentExpr() = checkIsBound()
    fun testLetDeclPatTupExpr() = checkIsBound()
    fun testLetDeclPatStructExpr() = checkIsBound()
    fun testLetDeclPatStructExprComplex() = checkIsBound()

    fun testStaticFnFromInherentImpl() = checkIsBound()
    fun testNonStaticFnFromInherentImpl() = checkIsUnbound()

    fun testHiddenInherentImpl() = checkIsBound()
    fun testWrongInherentImpl() = checkIsUnbound()
    fun testImplGenericsStripped() = checkIsBound()
}

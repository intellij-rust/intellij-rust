package org.rust.lang.core.resolve

class RustTypeResolveTestCase : RustResolveTestCaseBase() {
    override val dataPath = "org/rust/lang/core/resolve/fixtures/type"

    fun testSelfMethodCallExpr() = checkIsBound(atOffset = 27)
    fun testMethodCallExpr() = checkIsBound(atOffset = 27)
    fun testSelfFieldExpr() = checkIsBound()
    fun testFieldExpr() = checkIsBound()

    fun testLetDeclCallExpr() = checkIsBound()
    fun testLetDeclMethodCallExpr() = checkIsBound()
    fun testLetDeclPatIdentExpr() = checkIsBound()
    fun testLetDeclPatTupExpr() = checkIsBound()
    fun testLetDeclPatStructExpr() = checkIsBound()
    fun testLetDeclPatStructExprComplex() = checkIsBound()

    fun testStaticFnFromInherentImpl() = checkIsBound()
    fun testNonStaticFnFromInherentImpl() = checkIsUnbound()
}

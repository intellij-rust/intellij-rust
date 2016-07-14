package org.rust.lang.core.completion

class RustTypeAwareCompletionTest : RustCompletionTestBase() {

    override val dataPath = "org/rust/lang/core/completion/fixtures/type_aware"

    fun testMethodCallExpr() = checkSoleCompletion()
    fun testMethodCallExprRef() = checkSoleCompletion()
    fun testMethodCallExprEnum() = checkSoleCompletion()
    fun testFieldExpr() = checkSoleCompletion()
    fun testStaticMethod() = checkSoleCompletion()
}

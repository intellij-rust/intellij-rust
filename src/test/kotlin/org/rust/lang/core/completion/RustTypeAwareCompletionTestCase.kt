package org.rust.lang.core.completion

class RustTypeAwareCompletionTestCase: RustCompletionTestBase() {

    override val dataPath = "org/rust/lang/core/completion/fixtures/type_aware"

    fun testMethodCallExpr() = checkSoleCompletion()
    fun testFieldExpr() = checkSoleCompletion()
    fun testStaticMethod() = checkSoleCompletion()
}

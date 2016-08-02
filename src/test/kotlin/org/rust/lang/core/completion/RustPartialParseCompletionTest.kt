package org.rust.lang.core.completion

class RustPartialParseCompletionTest: RustCompletionTestBase() {
    override val dataPath = "org/rust/lang/core/completion/fixtures/partial_parse"

    fun testMatch() = checkSoleCompletion()
    fun testIfLet() = checkSoleCompletion()
    fun testWhileLet() = checkSoleCompletion()
    fun testIf() = checkSoleCompletion()
    fun testWhile() = checkSoleCompletion()
    fun testTypeParams() = checkSoleCompletion()
}


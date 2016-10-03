package org.rust.lang.core.completion

class RustPartialParseCompletionTest: RustCompletionTestBase() {
    override val dataPath = "org/rust/lang/core/completion/fixtures/partial_parse"

    fun testMatch() = checkSingleCompletionByFile()
    fun testIfLet() = checkSingleCompletionByFile()
    fun testWhileLet() = checkSingleCompletionByFile()
    fun testIf() = checkSingleCompletionByFile()
    fun testWhile() = checkSingleCompletionByFile()
    fun testTypeParams() = checkSingleCompletionByFile()
    fun testImpl() = checkSingleCompletionByFile()
    fun testImpl2() = checkSingleCompletionByFile()
    fun testImpl3() = checkSingleCompletionByFile()
    fun testLet() = checkSingleCompletionByFile()
    fun testImplMethodType() = checkSingleCompletionByFile()
    fun testStructField() = checkSingleCompletionByFile()
    fun testStatic() = checkSingleCompletionByFile()
    fun testConst() = checkSingleCompletionByFile()
}


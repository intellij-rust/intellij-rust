package org.rust.lang.core.completion

import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase

class RustCompletionTest : RustTestCaseBase() {

    override val dataPath = "org/rust/lang/core/completion/fixtures"

    private fun checkSoleCompletion() {
        checkByFile {
            val variants = myFixture.completeBasic()
            assertThat(variants)
                .withFailMessage("Expected a single completion, but got ${variants?.size}")
                .isNull()
        }
    }

    private fun checkNoCompletion() {
        myFixture.configureByFile(fileName)
        val variants = myFixture.completeBasic()
        assertThat(variants).isNotNull()
        assertThat(variants.size).isZero()
    }

    fun testLocalVariable()      = checkSoleCompletion()
    fun testFunctionName()       = checkSoleCompletion()
    fun testPath()               = checkSoleCompletion()
    fun testAnonymousItem()      = checkSoleCompletion()
    fun testLocalScope()         = checkNoCompletion()
}

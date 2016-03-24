package org.rust.lang.core.completion

import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase

class RustCompletionTest : RustTestCaseBase() {

    override val dataPath = "org/rust/lang/core/completion/fixtures"

    fun testLocalVariable()      = checkSoleCompletion()
    fun testFunctionName()       = checkSoleCompletion()
    fun testPath()               = checkSoleCompletion()
    fun testAnonymousItem()      = checkSoleCompletion()
    fun testIncompleteLet()      = checkSoleCompletion()

    fun testMultifile() = checkByDirectory {
        openFileInEditor("main.rs")
        executeSoloCompletion()
    }

    fun testLocalScope()         = checkNoCompletion()


    private fun checkSoleCompletion() = checkByFile {
        executeSoloCompletion()
    }

    private fun checkNoCompletion() {
        myFixture.configureByFile(fileName)
        val variants = myFixture.completeBasic()
        assertThat(variants).isNotNull()
        assertThat(variants.size).isZero()
    }

    private fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()
        assertThat(variants)
            .withFailMessage("Expected a single completion, but got ${variants?.size}")
            .isNull()
    }
}

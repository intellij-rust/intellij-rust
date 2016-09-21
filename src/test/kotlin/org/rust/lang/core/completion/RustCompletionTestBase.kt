package org.rust.lang.core.completion

import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase

abstract class RustCompletionTestBase : RustTestCaseBase() {

    protected fun checkSoleCompletion() = checkByFile {
        executeSoloCompletion()
    }

    protected fun checkNoCompletion() {
        myFixture.configureByFile(fileName)
        val variants = myFixture.completeBasic()
        assertThat(variants).isNotNull()
        assertThat(variants.size).isZero()
    }

    protected fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()
        if (variants != null) {
            error("Expected a single completion, but got ${variants.size}\n" +
                "${variants.toList()}")
        }
    }
}

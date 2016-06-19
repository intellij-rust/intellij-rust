package org.rust.completion

import org.rust.lang.core.completion.RustCompletionTestBase

class RustDeriveCompletionTest : RustCompletionTestBase() {
    override val dataPath = "org/rust/ide/completion/fixtures/derive_traits/"

    fun testCompleteOnStruct() = checkSoleCompletion()

    fun testCompleteOnEnum() = checkSoleCompletion()

    fun testDoesntCompleteOnFn() = checkNoCompletion()

    fun testDoesntCompleteOnMod() = checkNoCompletion()

    fun testDoesntCompleteNonDeriveAttr() = checkNoCompletion()

    fun testDoesntCompleteInnerAttr() = checkNoCompletion()

    fun testDoesntCompleteAlreadyDerived() = checkNoCompletion()
}

package org.rust.lang.core.completion

class RustDeriveCompletionTest : RustCompletionTestBase() {
    override val dataPath = "org/rust/lang/core/completion/fixtures/derive_traits/"

    fun testCompleteOnStruct() = checkSoleCompletion()

    fun testCompleteOnEnum() = checkSoleCompletion()

    fun testDoesntCompleteOnFn() = checkNoCompletion()

    fun testDoesntCompleteOnMod() = checkNoCompletion()

    fun testDoesntCompleteNonDeriveAttr() = checkNoCompletion()

    fun testDoesntCompleteInnerAttr() = checkNoCompletion()

    fun testDoesntCompleteAlreadyDerived() = checkNoCompletion()
}

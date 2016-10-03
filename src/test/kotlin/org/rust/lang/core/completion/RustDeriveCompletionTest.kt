package org.rust.lang.core.completion

class RustDeriveCompletionTest : RustCompletionTestBase() {
    override val dataPath = "org/rust/lang/core/completion/fixtures/derive_traits/"

    fun testCompleteOnStruct() = checkSoleCompletion()

    fun testCompleteOnEnum() = checkSoleCompletion()

    fun testDoesntCompleteOnFn() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        fn foo() { }
    """)

    fun testDoesntCompleteOnMod() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        mod foo { }
    """)

    fun testDoesntCompleteNonDeriveAttr() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        enum Test { Something }
    """)

    fun testDoesntCompleteInnerAttr() = checkNoCompletion("""
        mod bar {
            #![derive(PartialE/*caret*/)]
        }
    """)

    fun testDoesntCompleteAlreadyDerived() = checkNoCompletion("""
        #[derive(PartialEq, PartialE/*caret*/)]
        enum Test { Something }
    """)
}

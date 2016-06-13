package org.rust.completion

import com.intellij.codeInsight.completion.CompletionType
import org.rust.lang.RustTestCaseBase

class RustDeriveCompletionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/completion/fixtures/derive_traits/"

    fun testCompleteOnStruct() = checkByFile {
        openFileInEditor("complete_on_struct.rs")
        myFixture.complete(CompletionType.BASIC)
    }

    fun testCompleteOnEnum() = checkByFile {
        openFileInEditor("complete_on_enum.rs")
        myFixture.complete(CompletionType.BASIC)
    }

    fun testDoesntCompleteOnFn() = checkByFile {
        openFileInEditor("doesnt_complete_on_fn.rs")
        myFixture.complete(CompletionType.BASIC)
    }

    fun testDoesntCompleteOnMod() = checkByFile {
        openFileInEditor("doesnt_complete_on_mod.rs")
        myFixture.complete(CompletionType.BASIC)
    }

    fun testDoesntCompleteNonDeriveAttr() = checkByFile {
        openFileInEditor("doesnt_complete_non_derive_attr.rs")
        myFixture.complete(CompletionType.BASIC)
    }

    fun testDoesntCompleteInnerAttr() = checkByFile {
        openFileInEditor("doesnt_complete_inner_attr.rs")
        myFixture.complete(CompletionType.BASIC)
    }

    fun testDoesntCompleteAlreadyDerived() = checkByFile {
        openFileInEditor("doesnt_complete_already_derived.rs")
        myFixture.complete(CompletionType.BASIC)
    }
}

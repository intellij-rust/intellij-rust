package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class SetImmutableIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/set_immutable/"

    fun testSetImmutableVariable() = checkByFile {
        openFileInEditor("set_immutable_variable.rs")
        myFixture.launchAction(SetImmutableIntention())
    }

    fun testSetImmutableParameter() = checkByFile {
        openFileInEditor("set_immutable_parameter.rs")
        myFixture.launchAction(SetImmutableIntention())
    }
}

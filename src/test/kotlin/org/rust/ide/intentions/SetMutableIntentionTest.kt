package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class SetMutableIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/set_mutable/"

    fun testSetMutableVariable() = checkByFile {
        openFileInEditor("set_mutable_variable.rs")
        myFixture.launchAction(SetMutableIntention())
    }

    fun testSetMutableParameter() = checkByFile {
        openFileInEditor("set_mutable_parameter.rs")
        myFixture.launchAction(SetMutableIntention())
    }
}

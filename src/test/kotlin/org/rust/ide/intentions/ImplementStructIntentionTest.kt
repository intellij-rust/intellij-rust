package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class ImplementStructIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/implement_struct/"

    fun testImplementStruct() = checkByFile {
        openFileInEditor("implement_struct.rs")
        myFixture.launchAction(ImplementStructIntention())
    }
}

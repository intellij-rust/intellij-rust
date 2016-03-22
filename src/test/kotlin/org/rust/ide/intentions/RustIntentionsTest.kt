package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class RustIntentionsTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun testExpandModule() = checkByDirectory {
        openFileInEditor("foo.rs")
        myFixture.launchAction(ExpandModule())
    }

    fun testContractModule()  = checkByDirectory {
        openFileInEditor("other/mod.rs")
        myFixture.launchAction(ContractModule())
    }
}

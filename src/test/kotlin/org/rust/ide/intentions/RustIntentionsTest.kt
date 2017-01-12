package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class RustIntentionsTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun testContractModule() = checkByDirectory {
        openFileInEditor("other/mod.rs")
        myFixture.launchAction(ContractModuleIntention())
    }
}

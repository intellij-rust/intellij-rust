package org.rust.ide.intentions

import org.rust.lang.RsTestBase

class RsIntentionsTest : RsTestBase() {

    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun testContractModule() = checkByDirectory {
        openFileInEditor("other/mod.rs")
        myFixture.launchAction(ContractModuleIntention())
    }
}

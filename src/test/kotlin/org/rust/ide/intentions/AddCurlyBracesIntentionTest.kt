package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class AddCurlyBracesIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/add_curly_braces/"

    fun testAddCurlyBracesSimple() = checkByFile {
        openFileInEditor("add_curly_braces_simple.rs")
        myFixture.launchAction(AddCurlyBracesIntention())
    }

    fun testAddCurlyBracesLonger() = checkByFile {
        openFileInEditor("add_curly_braces_longer.rs")
        myFixture.launchAction(AddCurlyBracesIntention())
    }

    fun testAddCurlyBracesAlias() = checkByFile {
        openFileInEditor("add_curly_braces_alias.rs")
        myFixture.launchAction(AddCurlyBracesIntention())
    }

    fun testAddCurlyBracesExtra() = checkByFile {
        openFileInEditor("add_curly_braces_extra.rs")
        myFixture.launchAction(AddCurlyBracesIntention())
    }
}

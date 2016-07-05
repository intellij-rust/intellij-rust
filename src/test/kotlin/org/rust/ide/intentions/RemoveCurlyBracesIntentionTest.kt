package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class RemoveCurlyBracesIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/remove_curly_braces/"

    fun testRemoveCurlyBracesSimple() = checkByFile {
        openFileInEditor("remove_curly_braces_simple.rs")
        myFixture.launchAction(RemoveCurlyBracesIntention())
    }

    fun testRemoveCurlyBracesLonger() = checkByFile {
        openFileInEditor("remove_curly_braces_longer.rs")
        myFixture.launchAction(RemoveCurlyBracesIntention())
    }

    fun testRemoveCurlyBracesAlias() = checkByFile {
        openFileInEditor("remove_curly_braces_alias.rs")
        myFixture.launchAction(RemoveCurlyBracesIntention())
    }

    fun testRemoveCurlyBracesExtra() = checkByFile {
        openFileInEditor("remove_curly_braces_extra.rs")
        myFixture.launchAction(RemoveCurlyBracesIntention())
    }
}

package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class AddDeriveIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/add_derive/"

    fun testAddDeriveStruct() = checkByFile {
        openFileInEditor("add_derive_struct.rs")
        myFixture.launchAction(AddDerive())
    }

    fun testAddDeriveEnum() = checkByFile {
        // FIXME: there is something weird with enum re-formatting, for some reason it adds more indentation
        openFileInEditor("add_derive_enum.rs")
        myFixture.launchAction(AddDerive())
    }

    fun testAddDeriveExistingAttr() = checkByFile {
        openFileInEditor("add_derive_existing_attr.rs")
        myFixture.launchAction(AddDerive())
    }
}

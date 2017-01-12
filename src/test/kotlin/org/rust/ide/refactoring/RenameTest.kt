package org.rust.ide.refactoring

import org.rust.lang.RustTestCaseBase

class RenameTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/refactoring/fixtures/rename"

    fun testFunction() = doTest()
    fun testField() = doTest()

    private fun doTest(name: String = "spam") {
        myFixture.configureByFile(fileName)
        myFixture.renameElementAtCaret(name)
        myFixture.checkResultByFile(fileName, fileName.replace(".rs", "_after.rs"), false)
    }

    fun testRenameFile() = checkByDirectory {
        val file = myFixture.configureFromTempProjectFile("foo.rs")
        myFixture.renameElement(file, "bar.rs")
    }
}


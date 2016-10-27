package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class MoveTypeConstraintToWhereClauseTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/move_type_constraint_to_where_clause/"

    private fun doTest() = checkByFile {
        openFileInEditor(fileName)
        myFixture.launchAction(MoveTypeConstraintToWhereClauseIntention())
    }

    fun testFunctionWithReturn() = doTest()
    fun testLifetimesAndTraits() = doTest()
    fun testMultipleBounds() = doTest()
    fun testMultipleLifetimes() = doTest()
    fun testMultipleTraits() = doTest()
    fun testNoLifetimeBounds() = doTest()
    fun testNoTraitBounds() = doTest()
}

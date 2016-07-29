package org.rust.ide.surroundWith

class RustWithParenthesesSurrounderTest : RustSurrounderTestCaseBase(RustWithParenthesesSurrounder()) {
    fun testSurroundParentheses() = doTest()
    fun testSpacing() = doTest()
    fun testTrue1() = doTest()
    fun testTrue2() = doTest()
    fun testIdent1() = doTest()
    fun testIdent2() = doTest()

    fun testSelectPartOfExpression() = doTestNotApplicable()
    fun testSelectStatement() = doTestNotApplicable()
}


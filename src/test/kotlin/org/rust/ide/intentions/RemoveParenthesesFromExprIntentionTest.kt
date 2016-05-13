package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class RemoveParenthesesFromExprIntentionTest : RustTestCaseBase() {
    override val dataPath: String = "org/rust/ide/intentions/fixtures/remove_parentheses_from_expr/"

    fun testRemoveParenthesesFromExpr() = checkByFile {
        openFileInEditor("remove_parentheses_from_expr.rs")
        myFixture.launchAction(RemoveParenthesesFromExprIntention())
    }
}

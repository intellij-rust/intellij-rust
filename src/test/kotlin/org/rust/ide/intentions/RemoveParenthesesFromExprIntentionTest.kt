package org.rust.ide.intentions

class RemoveParenthesesFromExprIntentionTest : RsIntentionTestBase(RemoveParenthesesFromExprIntention()) {
    fun testRemoveParenthesesFromExpr() = doAvailableTest("""
        fn test() {
            let a = (4 + 3/*caret*/);
        }
    """, """
        fn test() {
            let a = 4 + 3/*caret*/;
        }
    """)
}

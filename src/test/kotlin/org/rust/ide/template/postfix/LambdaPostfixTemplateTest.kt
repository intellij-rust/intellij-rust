package org.rust.ide.template.postfix

class LambdaPostfixTemplateTest : PostfixTemplateTest(LambdaPostfixTemplate()) {
    fun testNotApplicable() = doTestNotApplicable("""
        struct S { }.lambda/*caret*/
    """)

    fun testSimple() = doTest("""
        fn foo() {
            let a = 4 + 4.lambda/*caret*/;
        }
    """, """
        fn foo() {
            let a = || 4 + 4/*caret*/;
        }
    """)
}

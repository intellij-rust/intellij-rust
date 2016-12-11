package org.rust.ide.template.postfix

class LambdaPostfixTemplateTest : PostfixTemplateTestCase(ParenPostfixTemplate()) {
    fun testNotApplicable() = doTestNotApplicable(
        """
        fn foo() {
            println!("test");.lambda/*caret*/
        }
        """
    )

    fun testSimple() = doTest(
        """
            fn foo() {
                let a = 4 + 4.lambda/*caret*/;
            }
            """
        ,
        """
            fn foo() {
                let a = || 4 + 4/*caret*/;
            }
            """
    )
}

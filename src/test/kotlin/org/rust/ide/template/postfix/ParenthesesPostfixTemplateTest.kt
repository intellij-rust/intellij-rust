package org.rust.ide.template.postfix

class ParenthesesPostfixTemplateTest : PostfixTemplateTestCase(ParenthesesPostfixTemplate()) {
    fun testNotApplicable() = doTestNotApplicable(
        """
        fn foo(a: i32, b: i32) {
            let a = 42;.par/*caret*/
        }
        """
    )
    fun testSimple() = doTest(
        """
        fn foo(a: i32, b: i32) {
            42.par/*caret*/
        }
        """
        ,
        """
        fn foo(a: i32, b: i32) {
            (42)/*caret*/
        }
        """
    )
}

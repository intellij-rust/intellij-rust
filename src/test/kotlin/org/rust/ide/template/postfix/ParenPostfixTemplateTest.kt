package org.rust.ide.template.postfix

class ParenPostfixTemplateTest : PostfixTemplateTestCase(ParenPostfixTemplate()) {
    fun testNotApplicable() = doTestNotApplicable(
        """
        fn main() {
            println!("test");.par/*caret*/
        }
        """
    )

    fun testSimple() = doTest(
        """
            fn foo() {
                4.par/*caret*/;
            }
        """, """
            fn foo() {
                (4)/*caret*/;
            }
        """
    )
}

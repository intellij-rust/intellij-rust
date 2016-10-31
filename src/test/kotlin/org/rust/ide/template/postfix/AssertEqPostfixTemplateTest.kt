package org.rust.ide.template.postfix

class AssertEqPostfixTemplateTest : PostfixTemplateTestCase(AssertEqPostfixTemplate()) {
    fun testNumber() = doTestNotApplicable(
            """
            fn main() {
                1234.asserteq/*caret*/
            }
            """
        )

    fun testBool() = doTestNotApplicable(
            """
            fn main() {
                true.asserteq/*caret*/
            }
            """
        )

    fun testNEQ() = doTestNotApplicable(
        """
        fn foo(a: i32, b: i32) {
            a != b.asserteq/*caret*/
        }
        """
    )

    fun testSimple1() = doTest(
        """
        fn foo(a: i32, b: i32) {
            a == b.asserteq/*caret*/
        }
        """
        ,
        """
        fn foo(a: i32, b: i32) {
            assert_eq!(a, b);/*caret*/
        }
        """
    )
}

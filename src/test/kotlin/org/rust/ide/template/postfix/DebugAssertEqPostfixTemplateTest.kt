package org.rust.ide.template.postfix

class DebugAssertEqPostfixTemplateTest : PostfixTemplateTestCase(DebugAssertEqPostfixTemplate()){
    fun testNumber() = doTestNotApplicable(
        """
        fn main() {
            1234.assertdebeq/*caret*/
        }
        """
    )

    fun testBool() = doTestNotApplicable(
        """
        fn main() {
            true.assertdebeq/*caret*/
        }
        """
    )

    fun testNEQ() = doTestNotApplicable(
        """
        fn foo(a: i32, b: i32) {
            a != b.assertdebeq/*caret*/
        }
        """
    )

    fun testSimple1() = doTest(
        """
        fn foo(a: i32, b: i32) {
            a == b.assertdebeq/*caret*/
        }
        """
        ,
        """
        fn foo(a: i32, b: i32) {
            debug_assert_eq!(a, b);/*caret*/
        }
        """
    )
}

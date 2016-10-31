package org.rust.ide.template.postfix

class DebugAssertPostfixTemplateTest : PostfixTemplateTestCase(DebugAssertPostfixTemplate()) {
    fun testNumber() = doTestNotApplicable(
        """
        fn main() {
            1234.assertdeb/*caret*/
        }
        """
    )

    fun testNumberCall() = doTestNotApplicable(
        """
        fn func() -> i32 {
            1234
        }
        fn main() {
            func().assertdeb/*caret*/
        }
        """
    )

    fun testSimple() = doTest(
        """
        fn main() {
            true.assertdeb/*caret*/
        }
        """
        ,
        """
        fn main() {
            debug_assert!(true);/*caret*/
        }
        """
    )

    fun testCall() = doTest(
        """
        fn func() -> bool {
            false
        }
        fn main() {
            func().assertdeb/*caret*/
        }
        """
        ,
        """
        fn func() -> bool {
            false
        }
        fn main() {
            debug_assert!(func());/*caret*/
        }
        """
    )
}

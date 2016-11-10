package org.rust.ide.template.postfix

class AssertPostfixTemplateTest : PostfixTemplateTestCase(AssertPostfixTemplate()) {
    fun testNumber() = doTestNotApplicable(
        """
        fn main() {
            1234.assert/*caret*/
        }
        """
    )

    fun testNumberCall() = doTestNotApplicable(
        """
        fn func() -> i32 {
            1234
        }
        fn main() {
            func().assert/*caret*/
        }
        """
    )

    fun testSimple() = doTest(
        """
        fn main() {
            true.assert/*caret*/
        }
        """
        ,
        """
        fn main() {
            assert!(true);/*caret*/
        }
        """
    )

    fun testCall() = doTest(
        """
        fn func() -> bool {
            false
        }
        fn main() {
            func().assert/*caret*/
        }
        """
        ,
        """
        fn func() -> bool {
            false
        }
        fn main() {
            assert!(func());/*caret*/
        }
        """
    )
}

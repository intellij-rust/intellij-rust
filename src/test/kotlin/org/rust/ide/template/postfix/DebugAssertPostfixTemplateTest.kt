/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class DebugAssertPostfixTemplateTest : PostfixTemplateTest(DebugAssertPostfixTemplate()) {
    fun testNumber() = doTestNotApplicable(
        """
        fn main() {
            1234.debug_assert/*caret*/
        }
        """
    )

    fun testSimple() = doTest(
        """
        fn main() {
            true.debug_assert/*caret*/
        }
        """
        ,
        """
        fn main() {
            debug_assert!(true);/*caret*/
        }
        """
    )

    fun testNEQ() = doTest(
        """
        fn foo(a: i32, b: i32) {
            a != b.debug_assert/*caret*/
        }
        """
        ,
        """
        fn foo(a: i32, b: i32) {
            debug_assert!(a != b);/*caret*/
        }
        """
    )

    fun testSimple1() = doTest(
        """
        fn foo(a: i32, b: i32) {
            a == b.debug_assert/*caret*/
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

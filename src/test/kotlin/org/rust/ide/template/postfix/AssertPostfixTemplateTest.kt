/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class AssertPostfixTemplateTest : PostfixTemplateTest(AssertPostfixTemplate()) {
    fun testNumber() = doTestNotApplicable(
        """
        fn main() {
            1234.assert/*caret*/
        }
        """
    )

    fun testSimple() = doTest(
        """
        fn main() {
            let a = true;
            a.assert/*caret*/
        }
        """
        ,
        """
        fn main() {
            let a = true;
            assert!(a);/*caret*/
        }
        """
    )

    fun testNEQ() = doTest(
        """
        fn foo(a: i32, b: i32) {
            a != b.assert/*caret*/
        }
        """
        ,
        """
        fn foo(a: i32, b: i32) {
            assert!(a != b);/*caret*/
        }
        """
    )

    fun testSimple1() = doTest(
        """
        fn foo(a: i32, b: i32) {
            a == b.assert/*caret*/
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

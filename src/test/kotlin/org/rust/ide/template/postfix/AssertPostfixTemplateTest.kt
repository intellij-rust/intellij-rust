/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class AssertPostfixTemplateTest : RsPostfixTemplateTest(AssertPostfixTemplate(RsPostfixTemplateProvider())) {
    fun `test not boolean expr`() = doTestNotApplicable("""
        fn main() {
            1234.assert/*caret*/
        }
    """)

    fun `test boolean expr 1`() = doTest("""
        fn main() {
            let a = true;
            a.assert/*caret*/
        }
    """, """
        fn main() {
            let a = true;
            assert!(a);/*caret*/
        }
    """)

    fun `test boolean expr 2`() = doTest("""
        fn foo(a: i32, b: i32) {
            a != b.assert/*caret*/
        }
    """, """
        fn foo(a: i32, b: i32) {
            assert!(a != b);/*caret*/
        }
    """)

    fun `test equality expr`() = doTest("""
        fn foo(a: i32, b: i32) {
            a == b.assert/*caret*/
        }
    """, """
        fn foo(a: i32, b: i32) {
            assert_eq!(a, b);/*caret*/
        }
    """)
}

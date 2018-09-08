/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class DebugAssertPostfixTemplateTest : PostfixTemplateTest(DebugAssertPostfixTemplate(RsPostfixTemplateProvider())) {
    fun `test not boolean expr`() = doTestNotApplicable("""
        fn main() {
            1234.debug_assert/*caret*/
        }
    """)

    fun `test boolean expr 1`() = doTest("""
        fn main() {
            true.debug_assert/*caret*/
        }
    """, """
        fn main() {
            debug_assert!(true);/*caret*/
        }
    """)

    fun `test boolean expr 2`() = doTest("""
        fn foo(a: i32, b: i32) {
            a != b.debug_assert/*caret*/
        }
    """, """
        fn foo(a: i32, b: i32) {
            debug_assert!(a != b);/*caret*/
        }
    """)

    fun `test equality expr`() = doTest("""
        fn foo(a: i32, b: i32) {
            a == b.debug_assert/*caret*/
        }
    """, """
        fn foo(a: i32, b: i32) {
            debug_assert_eq!(a, b);/*caret*/
        }
    """)
}

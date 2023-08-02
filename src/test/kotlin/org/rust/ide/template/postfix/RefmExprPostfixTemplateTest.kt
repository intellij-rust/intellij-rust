/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class RefmExprPostfixTemplateTest : RsPostfixTemplateTest(RefmExprPostfixTemplate::class) {
    fun `test expression`() = doTest("""
        fn main() {
            let mut v = vec![1, 2, 3];
            foo(v.refm/*caret*/);
        }

        fn foo(v: &mut Vec<i32>) {}
    """, """
        fn main() {
            let mut v = vec![1, 2, 3];
            foo(&mut v/*caret*/);
        }

        fn foo(v: &mut Vec<i32>) {}
    """)

    fun `test type reference`() = doTest("""
        fn foo(a: str.refm/*caret*/) {}
    """, """
        fn foo(a: &mut str/*caret*/) {}
    """)
}

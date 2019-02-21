/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class RefmPostfixTemplateTest : RsPostfixTemplateTest(RefmPostfixTemplate(RsPostfixTemplateProvider())) {
    fun `test simple`() = doTest("""
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
}

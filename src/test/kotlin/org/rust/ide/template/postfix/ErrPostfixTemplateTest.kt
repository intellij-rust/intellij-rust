/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class ErrPostfixTemplateTest : RsPostfixTemplateTest(ErrPostfixTemplate(RsPostfixTemplateProvider())) {

    fun `test expr`() = doTest("""
        fn foo(slice: &[i32]) {
            let first = slice[0].err/*caret*/;
        }
    """, """
        fn foo(slice: &[i32]) {
            let first = Err(slice[0])/*caret*/;
        }
    """)

    fun `test not expr`() = doTestNotApplicable("""
        fn main() {
            println!("Hello!");.err/*caret*/
        }
    """)
}

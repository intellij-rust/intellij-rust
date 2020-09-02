/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class DbgrPostfixTemplateTest : RsPostfixTemplateTest(DbgrPostfixTemplate(RsPostfixTemplateProvider())) {

    fun `test expr`() = doTest("""
        fn foo(slice: &[i32]) {
            let first = slice[0];
            first.dbgr/*caret*/;
        }
    """, """
        fn foo(slice: &[i32]) {
            let first = slice[0];
            dbg!(&first)/*caret*/;
        }
    """)

    fun `test not expr`() = doTestNotApplicable("""
        fn main() {
            println!("Hello!");.dbgr/*caret*/
        }
    """)
}

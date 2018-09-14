/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class ParenPostfixTemplateTest : RsPostfixTemplateTest(ParenPostfixTemplate()) {
    fun `test not expr`() = doTestNotApplicable("""
        fn main() {
            println!("test");.par/*caret*/
        }
    """)

    fun `test simple expr`() = doTest("""
        fn foo() {
            4.par/*caret*/;
        }
    """, """
        fn foo() {
            (4)/*caret*/;
        }
    """)
}

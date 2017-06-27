/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class ParenPostfixTemplateTest : PostfixTemplateTest(ParenPostfixTemplate()) {
    fun testNotApplicable() = doTestNotApplicable(
        """
        fn main() {
            println!("test");.par/*caret*/
        }
        """
    )

    fun testSimple() = doTest(
        """
            fn foo() {
                4.par/*caret*/;
            }
        """, """
            fn foo() {
                (4)/*caret*/;
            }
        """
    )
}

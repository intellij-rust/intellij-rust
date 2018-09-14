/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class LambdaPostfixTemplateTest : RsPostfixTemplateTest(LambdaPostfixTemplate(RsPostfixTemplateProvider())) {
    fun `test not applicable`() = doTestNotApplicable("""
        struct S { }.lambda/*caret*/
    """)

    fun `test simple`() = doTest("""
        fn foo() {
            let a = 4 + 4.lambda/*caret*/;
        }
    """, """
        fn foo() {
            let a = || 4 + 4/*caret*/;
        }
    """)
}

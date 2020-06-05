/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class ErrPostfixTemplateTest : RsPostfixTemplateTest(ErrPostfixTemplate(RsPostfixTemplateProvider())) {

    fun `test expr`() = doTest("""
        fn foo(number: i32) -> Result<(), i32> {
            number.err/*caret*/
        }
    """, """
        fn foo(number: i32) -> Result<(), i32> {
            Err(number)
        }
    """)

    fun `test not expr`() = doTestNotApplicable("""
        fn main() {
            println!("Hello!");.err/*caret*/
        }
    """)
}

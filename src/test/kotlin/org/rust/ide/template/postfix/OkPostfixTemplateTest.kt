/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class OkPostfixTemplateTest : RsPostfixTemplateTest(OkPostfixTemplate(RsPostfixTemplateProvider())) {

    fun `test expr`() = doTest("""
        fn foo(number: i32) -> Result<i32, ()> {
            number.ok/*caret*/
        }
    """, """
        fn foo(number: i32) -> Result<i32, ()> {
            Ok(number)
        }
    """)

    fun `test not expr`() = doTestNotApplicable("""
        fn main() {
            println!("Hello!");.ok/*caret*/
        }
    """)
}

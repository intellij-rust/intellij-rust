/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class SomePostfixTemplateTest : RsPostfixTemplateTest(SomePostfixTemplate(RsPostfixTemplateProvider())) {

    fun `test expr`() = doTest("""
        fn foo(number: i32) -> Option<i32> {
            number.some/*caret*/
        }
    """, """
        fn foo(number: i32) -> Option<i32> {
            Some(number)
        }
    """)

    fun `test not expr`() = doTestNotApplicable("""
        fn main() {
            println!("Hello!");.some/*caret*/
        }
    """)
}

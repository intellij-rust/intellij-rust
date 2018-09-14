/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class WhilePostfixTemplateTest : RsPostfixTemplateTest(WhileExpressionPostfixTemplate()) {
    fun `test not boolean expr 1`() = doTestNotApplicable("""
        fn main() {
            let a = 4;
            a.while/*caret*/
        }
    """)

    fun `test not boolean expr 2`() = doTestNotApplicable("""
        fn func() -> i32 {
            1234
        }

        fn main() {
            func().while/*caret*/
        }
    """)

    fun `test boolean expr`() = doTest("""
        fn main() {
            let a = 4 == 2;
            a.while/*caret*/
        }
    """, """
        fn main() {
            let a = 4 == 2;
            while a {/*caret*/}
        }
    """)

    fun `test fun arg`() = doTest("""
        fn foo(a: bool) {
            a.while/*caret*/
        }
    """, """
        fn foo(a: bool) {
            while a {/*caret*/}
        }
    """)

    fun `test negated boolean expr`() = doTest("""
        fn main() {
            let a = 4 == 2;
            !a.while/*caret*/
        }
    """, """
        fn main() {
            let a = 4 == 2;
            while !a {/*caret*/}
        }
    """)

    fun `test simple eq expr`() = doTest("""
        fn main() {
            true == true.while/*caret*/
        }
    """, """
        fn main() {
            while true == true {/*caret*/}
        }
    """)

    fun `test selector`() = doTest("""
        fn main() {
            let a = if true {
                true == false.while/*caret*/
            } else {
                false == true
            };
        }
    """, """
        fn main() {
            let a = if true {
                while true == false {/*caret*/}
            } else {
                false == true
            };
        }
    """)

    fun `test call`() = doTest("""
        fn func() -> bool {
            false
        }

        fn main() {
            func().while/*caret*/
        }
    """, """
        fn func() -> bool {
            false
        }

        fn main() {
            while func() {/*caret*/}
        }
    """)
}

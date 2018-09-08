/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class IfPostfixTemplateTest : PostfixTemplateTest(IfExpressionPostfixTemplate()) {
    fun `test not boolean expr 1`() = doTestNotApplicable("""
        fn main() {
            let a = 4;
            a.if/*caret*/
        }
    """)

    fun `test not boolean expr 2`() = doTestNotApplicable("""
        fn func() -> i32 {
            1234
        }

        fn main() {
            func().if/*caret*/
        }
    """)

    fun `test boolean expr`() = doTest("""
        fn main() {
            let a = 4 == 2;
            a.if/*caret*/
        }
    """, """
        fn main() {
            let a = 4 == 2;
            if a {/*caret*/}
        }
    """)

    fun `test fun arg`() = doTest("""
        fn foo(a: bool) {
            a.if/*caret*/
        }
    """, """
        fn foo(a: bool) {
            if a {/*caret*/}
        }
    """)

    fun `test negated boolean expr`() = doTest("""
        fn main() {
            let a = 4 == 2;
            !a.if/*caret*/
        }
    """, """
        fn main() {
            let a = 4 == 2;
            if !a {/*caret*/}
        }
    """)

    fun `test simple eq expr`() = doTest("""
        fn main() {
            true == true.if/*caret*/
        }
    """, """
        fn main() {
            if true == true {/*caret*/}
        }
    """)


    fun `test selector`() = doTest("""
        fn main() {
            let a = if (true) {
                true == false.if/*caret*/
            } else {
                false == true
            };
        }
    """, """
        fn main() {
            let a = if (true) {
                if true == false {/*caret*/}
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
            func().if/*caret*/
        }
    """, """
        fn func() -> bool {
            false
        }

        fn main() {
            if func() {/*caret*/}
        }
    """)
}

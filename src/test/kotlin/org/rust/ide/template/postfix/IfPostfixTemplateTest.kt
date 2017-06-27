/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class IfPostfixTemplateTest : PostfixTemplateTest(IfExpressionPostfixTemplate()) {
    fun testNumber() = doTestNotApplicable(
        """
            fn main() {
                let a = 4;
                a.if/*caret*/
            }
            """
    )

    fun testNumberCall() = doTestNotApplicable(
        """
            fn func() -> i32 {
                1234
            }

            fn main() {
                func().if/*caret*/
            }
            """
    )

    fun testSimple() = doTest(
        """
            fn main() {
                let a = 4 == 2;
                a.if/*caret*/
            }
            """
        ,
        """
            fn main() {
                let a = 4 == 2;
                if a {/*caret*/}
            }
            """
    )

    fun testFunArg() = doTest(
        """
                fn foo(a: bool) {
                    a.if/*caret*/
                }
                """
        ,
        """
                fn foo(a: bool) {
                    if a {/*caret*/}
                }
                """
    )

    fun testSimpleNegatedExpr() = doTest(
        """
            fn main() {
                let a = 4 == 2;
                !a.if/*caret*/
            }
            """
        ,
        """
            fn main() {
                let a = 4 == 2;
                if !a {/*caret*/}
            }
            """
    )

    fun testSimpleEqExpr() = doTest(
        """
            fn main() {
                true == true.if/*caret*/
            }
            """
        ,
        """
            fn main() {
                if true == true {/*caret*/}
            }
            """
    )


    fun testSelector() = doTest(
        """
            fn main() {
                let a = if (true) {
                    true == false.if/*caret*/
                } else {
                    false == true
                };
            }
            """
        ,
        """
            fn main() {
                let a = if (true) {
                    if true == false {/*caret*/}
                } else {
                    false == true
                };
            }
            """
    )

    fun testCall() = doTest(
        """
        fn func() -> bool {
            false
        }

        fn main() {
            func().if/*caret*/
        }
        """
        ,
        """
        fn func() -> bool {
            false
        }

        fn main() {
            if func() {/*caret*/}
        }
        """
    )
}

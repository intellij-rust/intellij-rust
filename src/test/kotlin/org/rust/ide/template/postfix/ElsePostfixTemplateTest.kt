/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class ElsePostfixTemplateTest : PostfixTemplateTest(ElseExpressionPostfixTemplate()) {
    fun testNumber() = doTestNotApplicable(
        """
            fn main() {
                let a = 4;
                a.else/*caret*/
            }
            """
    )

    fun testNumberCall() = doTestNotApplicable(
        """
            fn func() -> i32 {
                1234
            }

            fn main() {
                func().else/*caret*/
            }
            """
    )

    fun testSimple() = doTest(
        """
            fn main() {
                let a = 4 == 2;
                a.else/*caret*/
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

    fun testSimpleNegatedExpr() = doTest(
        """
            fn main() {
                let a = 4 == 2;
                !a.else/*caret*/
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
                a.else/*caret*/
            }
            """
        ,
        """
            fn foo(a: bool) {
                if !a {/*caret*/}
            }
            """
    )

    fun testSimpleEqExpr() = doTest(
        """
            fn main() {
                true == true.else/*caret*/
            }
            """
        ,
        """
            fn main() {
                if true != true {/*caret*/}
            }
            """
    )


    fun testSelector() = doTest(
        """
            fn main() {
                let a = if (true) {
                    42 < 43.else/*caret*/
                } else {
                    false == true
                };
            }
            """
        ,
        """
            fn main() {
                let a = if (true) {
                    if 42 >= 43 {/*caret*/}
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
            func().else/*caret*/
        }
        """
        ,
        """
        fn func() -> bool {
            false
        }

        fn main() {
            if !func() {/*caret*/}
        }
        """
    )

    fun testBinOperatorsBool() {
        val cases = listOf(
            Pair("1 == 2", "1 != 2"),
            Pair("1 != 2", "1 == 2"),
            Pair("1 <= 2", "1 > 2"),
            Pair("1 >= 2", "1 < 2"),
            Pair("1 < 2", "1 >= 2"),
            Pair("1 > 2", "1 <= 2"),
            Pair("!(1 == 2)", "1 == 2"),
            Pair("(1 == 2)", "!(1 == 2)")
        )

        for (case in cases) {
            doTest(
                """
                fn main() {
                    ${case.first}.else/*caret*/
                }
                """
                ,
                """
                fn main() {
                    if ${case.second} {/*caret*/}
                }
                """
            )
        }
    }
}

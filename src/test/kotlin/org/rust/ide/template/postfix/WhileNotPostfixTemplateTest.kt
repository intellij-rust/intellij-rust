/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class WhileNotPostfixTemplateTest : PostfixTemplateTest(WhileNotExpressionPostfixTemplate()) {
    fun testNumber() = doTestNotApplicable(
        """
            fn main() {
                let a = 4;
                a.whilenot/*caret*/
            }
            """
    )

    fun testNumberCall() = doTestNotApplicable(
        """
            fn func() -> i32 {
                1234
            }

            fn main() {
                func().whilenot/*caret*/
            }
            """
    )

    fun testSimple() = doTest(
        """
            fn main() {
                let a = 4 == 2;
                a.whilenot/*caret*/
            }
            """
        ,
        """
            fn main() {
                let a = 4 == 2;
                while !a {/*caret*/}
            }
            """
    )

    fun testSimpleNegatedExpr() = doTest(
        """
            fn main() {
                let a = 4 == 2;
                !a.whilenot/*caret*/
            }
            """
        ,
        """
            fn main() {
                let a = 4 == 2;
                while a {/*caret*/}
            }
            """
    )

    fun testFunArg() = doTest(
        """
            fn foo(a: bool) {
                a.whilenot/*caret*/
            }
            """
        ,
        """
            fn foo(a: bool) {
                while !a {/*caret*/}
            }
            """
    )

    fun testSimpleEqExpr() = doTest(
        """
            fn main() {
                true == true.whilenot/*caret*/
            }
            """
        ,
        """
            fn main() {
                while true != true {/*caret*/}
            }
            """
    )


    fun testSelector() = doTest(
        """
            fn main() {
                let a = if (true) {
                    42 < 43.whilenot/*caret*/
                } else {
                    false == true
                };
            }
            """
        ,
        """
            fn main() {
                let a = if (true) {
                    while 42 >= 43 {/*caret*/}
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
            func().whilenot/*caret*/
        }
        """
        ,
        """
        fn func() -> bool {
            false
        }

        fn main() {
            while !func() {/*caret*/}
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
                    ${case.first}.whilenot/*caret*/
                }
                """
                ,
                """
                fn main() {
                    while ${case.second} {/*caret*/}
                }
                """
            )
        }
    }
}

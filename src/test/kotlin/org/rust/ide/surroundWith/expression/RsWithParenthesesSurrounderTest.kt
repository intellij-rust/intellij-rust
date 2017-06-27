/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.expression

import org.rust.ide.surroundWith.RsSurrounderTestBase

class RsWithParenthesesSurrounderTest : RsSurrounderTestBase(RsWithParenthesesSurrounder()) {
    fun testSelectPartOfExpression() {
        doTestNotApplicable(
            """
            fn main() {
                let a = {
                    let inner = 3;
                    inner <selection>* inner</selection>
                };
            }
            """
        )
    }

    fun testSelectStatement() {
        doTestNotApplicable(
            """
            fn main() {
                let a = {
                    <selection>let inner = 3;</selection>
                    inner * inner
                };
            }
            """
        )
    }

    fun testSurroundParentheses() {
        doTest(
            """
            fn main() {
                let a = {
                    let inner = 3;
                    <selection>inner * inner</selection>
                };
            }
            """
            ,
            """
            fn main() {
                let a = {
                    let inner = 3;
                    (inner * inner)<caret>
                };
            }
            """
        )
    }

    fun testSpacing() {
        doTest(
            """
            fn main() {
                let _ = <selection>  1 + 1  </selection>;
            }
            """,
            """
            fn main() {
                let _ = (1 + 1)<caret>;
            }
            """
        )
    }

    fun testTrue1() {
        doTest(
            """
            fn foo() -> bool {
                tr<caret>ue
            }
            """
            ,
            """
            fn foo() -> bool {
                (true)<caret>
            }
            """
        )
    }

    fun testTrue2() {
        doTest(
            """
            fn foo() -> bool {
                t<selection>ru</selection>e
            }
            """
            ,
            """
            fn foo() -> bool {
                (true)<caret>
            }
            """
        )
    }

    fun testIdent1() {
        doTest(
            """
            fn foo() -> bool {
                fo<caret>o
            }
            """
            ,
            """
            fn foo() -> bool {
                (foo)<caret>
            }
            """
        )
    }

    fun testIdent2() {
        doTest(
            """
            fn foo() -> bool {
                f<selection>o</selection>o
            }
            """
            ,
            """
            fn foo() -> bool {
                (foo)<caret>
            }
            """
        )
    }
}


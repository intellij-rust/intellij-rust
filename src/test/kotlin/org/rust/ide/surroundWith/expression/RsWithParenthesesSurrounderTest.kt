/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.expression

import org.rust.ide.surroundWith.RsSurrounderTestBase

class RsWithParenthesesSurrounderTest : RsSurrounderTestBase(RsWithParenthesesSurrounder()) {
    fun `test select part of expression`() = doTestNotApplicable("""
        fn main() {
            let a = {
                let inner = 3;
                inner <selection>* inner</selection>
            };
        }
    """)

    fun `test select statement`() = doTestNotApplicable("""
        fn main() {
            let a = {
                <selection>let inner = 3;</selection>
                inner * inner
            };
        }
    """)

    fun `test surround parentheses`() = doTest("""
        fn main() {
            let a = {
                let inner = 3;
                <selection>inner * inner</selection>
            };
        }
    """, """
        fn main() {
            let a = {
                let inner = 3;
                (inner * inner)<caret>
            };
        }
    """)

    fun `test spacing`() = doTest("""
        fn main() {
            let _ = <selection>  1 + 1  </selection>;
        }
    """, """
        fn main() {
            let _ = (1 + 1)<caret>;
        }
    """)

    fun `test true 1`() = doTest("""
        fn foo() -> bool {
            tr<caret>ue
        }
    """, """
        fn foo() -> bool {
            (true)<caret>
        }
    """)

    fun `test true 2`() = doTest("""
        fn foo() -> bool {
            t<selection>ru</selection>e
        }
    """, """
        fn foo() -> bool {
            (true)<caret>
        }
    """)

    fun `test ident 1`() = doTest("""
        fn foo() -> bool {
            fo<caret>o
        }
    """, """
        fn foo() -> bool {
            (foo)<caret>
        }
    """)

    fun `test ident 2`() = doTest("""
        fn foo() -> bool {
            f<selection>o</selection>o
        }
    """, """
        fn foo() -> bool {
            (foo)<caret>
        }
    """)
}

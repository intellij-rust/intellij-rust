/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.types.liveness
import org.rust.stdext.mapToSet

class RsLivenessTest : RsTestBase() {
    fun `test one last usage 1`() = check("""
        fn main() {
            let x = 42;
            consume(x);
                  //^ last
        }
    """)

    fun `test one last usage 2`() = check("""
        fn main() {
            let x = 42;
            consume1(x);
            consume2(x);
                   //^ last
        }
    """)

    fun `test one last usage if`() = check("""
        fn main() {
            let x = 42;
            consume1(x);
            if flag {
                consume2(x);
                       //^ last
            }
        }
    """)

    fun `test two last usages if else`() = check("""
        fn main() {
            let x = 42;
            if flag {
                consume1(x);
                       //^ last
            } else {
                consume2(x);
                       //^ last
            }
        }
    """)

    fun `test one last usage after if`() = check("""
        fn main() {
            let x = 42;
            if flag {
                consume1(x);
            }
            consume2(x);
                   //^ last
        }
    """)

    fun `test one last usage second if`() = check("""
        fn main() {
            let x = 42;
            if flag1 {
                consume1(x);
            }
            if flag2 {
                consume2(x);
                       //^ last
            }
        }
    """)

    fun `test two last usages if return`() = check("""
        fn main() {
            let x = 42;
            if flag {
                consume1(x);
                       //^ last
                return;
            }
            consume2(x);
                   //^ last
        }
    """)

    fun `test one last usage path`() = check("""
        struct S { x: i32 }
        fn main() {
            let s = S { x: 42 };
            consume(s.x);
                  //^ last
        }
    """)

    fun `test one last usage path if else`() = check("""
        struct S { x: i32, y: i32 }
        fn main() {
            let s = S { x: 42, y: 42 };
            consume(s);
            consume(s.x);
            consume(s.y);
            if flag {
                consume1(s.x);
                       //^ last
            } else {
                consume2(s.y);
                       //^ last
            }
        }
    """)

    private fun check(@Language("Rust") code: String) {
        InlineFile(code)

        val expectedLastUsages = findElementsWithDataAndOffsetInEditor<RsPathExpr>().mapToSet { it.first }
        val func = expectedLastUsages.first().ancestorStrict<RsFunction>()!!
        val actualLastUsages = func.liveness!!.lastUsages.values.single().toSet()

        if (expectedLastUsages != actualLastUsages) {
            val expectedStmtsText = expectedLastUsages.joinToString("\n") { it.ancestorOrSelf<RsExprStmt>()!!.text }
            val actualStmtsText = actualLastUsages.joinToString("\n") { it.ancestorOrSelf<RsExprStmt>()!!.text }

            // try to show the difference in a more readable format first
            assertEquals(expectedStmtsText, actualStmtsText)
            // otherwise, fail with PSI comparison
            assertEquals(expectedLastUsages, actualLastUsages)
        }
    }
}

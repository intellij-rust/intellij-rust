/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.lang.core.psi.RsElementTypes.*

class FlipBinaryExpressionIntentionTest : RsIntentionTestBase(FlipBinaryExpressionIntention()) {

    private val targetIntention = intention as FlipBinaryExpressionIntention

    fun `test all avaiable operators`() {
        val operators = FlipBinaryExpressionIntention.COMMUNICATIVE_OPERATORS +
            FlipBinaryExpressionIntention.CHANGE_SEMANTICS_OPERATORS +
            FlipBinaryExpressionIntention.COMPARISON_OPERATORS
        operators.map {
            when (it) {
                GTGT -> ">>"
                GTEQ -> ">="
                LTLT -> "<<"
                LTEQ -> "<="
                OROR -> "||"
                else -> it.toString()
            }
        }.forEach {
            doTest(it)
        }
    }

    fun doTest(op: String) {
        val flippedOp = (intention as FlipBinaryExpressionIntention).flippedOp(op)
        doAvailableTest("""
            fn test(x: i32, y: i32) {
                x /*caret*/$op y;
            }
        """, """
            fn test(x: i32, y: i32) {
                y /*caret*/$flippedOp x;
            }
        """)
    }

}

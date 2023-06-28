/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsRangeExpr

val RsRangeExpr.isInclusive: Boolean
    get() = dotdotdot != null || dotdoteq != null

val RsRangeExpr.isExclusive: Boolean
    get() = dotdot != null

val RsRangeExpr.op: PsiElement?
    get() = dotdot ?: dotdotdot ?: dotdoteq

val RsRangeExpr.start: RsExpr?
    get() {
        val op = op ?: return null
        return exprList.firstOrNull()?.takeIf { it.endOffset <= op.startOffset }
    }

val RsRangeExpr.end: RsExpr?
    get() {
        val op = op ?: return null
        return exprList.lastOrNull()?.takeIf { it.startOffset >= op.endOffset }
    }

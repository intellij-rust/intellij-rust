/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsBreakExpr
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.openapiext.forEachChild

interface RsLabeledExpression : RsElement {
    val labelDecl: RsLabelDecl?
    val block: RsBlock?
}

fun RsLabeledExpression.processBreakExprs(
    label: String?,
    matchOnlyByLabel: Boolean,
    sink: (RsBreakExpr) -> Unit
) = processBreakExprs(this, label, matchOnlyByLabel, sink)

private fun processBreakExprs(
    element: PsiElement,
    label: String?,
    matchOnlyByLabel: Boolean,
    sink: (RsBreakExpr) -> Unit
) {
    element.forEachChild { child ->
        when (child) {
            is RsBreakExpr -> {
                processBreakExprs(child, label, matchOnlyByLabel, sink)
                if (!matchOnlyByLabel && child.label == null || child.label?.referenceName == label) {
                    sink(child)
                }
            }
            is RsLooplikeExpr -> {
                if (label != null) {
                    processBreakExprs(child, label, true, sink)
                }
            }
            else -> processBreakExprs(child, label, matchOnlyByLabel, sink)
        }
    }
}

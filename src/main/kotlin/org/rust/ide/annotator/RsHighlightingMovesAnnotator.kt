/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.RsInferenceContextOwner
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.existsAfterExpansion
import org.rust.lang.core.types.borrowCheckResult
import org.rust.openapiext.isUnitTestMode

class RsHighlightingMovesAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (holder.isBatchMode) return
        if (element.elementType != IDENTIFIER) return
        val path = element.parent as? RsPath ?: return
        val pathExpr = path.parent as? RsPathExpr ?: return
        val owner = pathExpr.ancestorStrict<RsInferenceContextOwner>() as? RsFunction ?: return
        val borrowCheckResult = owner.borrowCheckResult ?: return
        if (pathExpr in borrowCheckResult.allMoves) {
            val crate = holder.currentCrate()
            if (crate != null && !pathExpr.existsAfterExpansion(crate)) return

            val color = RsColor.UNSAFE_CODE
            val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION

            holder.newAnnotation(severity, "Move")
                .range(element)
                .textAttributes(color.textAttributesKey).create()
        }
    }
}

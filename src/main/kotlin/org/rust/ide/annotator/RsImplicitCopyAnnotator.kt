/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.type

class RsImplicitCopyAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val rsHolder = RsAnnotationHolder(holder)
        val visitor = object : RsVisitor() {
            override fun visitPathExpr(o: RsPathExpr) = checkPathExpr(o, rsHolder)
        }

        element.accept(visitor)
    }

    fun checkPathExpr(expr: RsPathExpr, holder: RsAnnotationHolder) {
        val binding = expr.path.reference?.resolve() as? RsPatBinding ?: return
        val type = binding.type
        val lookup = binding.implLookup
        if (lookup.isCopy(type)) {
            holder.holder.createCopyAnnotation(expr.textRange)
        }
    }

    private fun AnnotationHolder.createCopyAnnotation(textRange: TextRange) {
        if (isBatchMode) return
        val color = RsColor.IMPLICIT_COPY
        val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION

        newSilentAnnotation(severity)
            .range(textRange)
            .textAttributes(color.textAttributesKey)
            .create()
    }
}

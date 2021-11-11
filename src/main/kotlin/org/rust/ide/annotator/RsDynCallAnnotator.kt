/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.cargo.project.settings.getDynCallHighlightingEnabled
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.TyTraitObject
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.openapiext.isUnitTestMode

class RsDynCallAnnotator : AnnotatorBase() {
    companion object {
        private val LOG: Logger = logger<RsDynCallAnnotator>()
    }

    private val message = RsBundle.message("annotator.dyn.call.message")

    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (!getDynCallHighlightingEnabled()) return
        val rsHolder = RsAnnotationHolder(holder)
        val visitor = object : RsVisitor() {
            override fun visitMethodCall(o: RsMethodCall) = checkMethodCall(o, rsHolder)
            override fun visitCallExpr(o: RsCallExpr) = checkCallExpr(o, rsHolder)
        }
        element.accept(visitor)
    }

    private fun annotateDynCall(expr: RsExpr, holder: RsAnnotationHolder) {
        if (!expr.existsAfterExpansion) return

        val textRange = when (expr) {
            is RsCallExpr -> when (val callee = expr.expr) {
                is RsPathExpr -> callee.path.textRangeOfLastSegment ?: return
                else -> callee.textRange
            }
            is RsDotExpr -> when (val call = expr.methodCall) {
                null -> return // unreachable
                else -> call.textRangeWithoutValueArguments
            }
            else -> return // unreachable
        }
        holder.holder.createDynCallAnnotation(textRange, message)
    }

    private fun checkMethodCall(o: RsMethodCall, holder: RsAnnotationHolder) {
        val ty = o.inference?.getResolvedMethod(o)?.singleOrNull()?.selfTy
        if (ty is TyTraitObject) {
            holder.holder.createDynCallAnnotation(o.textRangeWithoutValueArguments, message)
        }
    }

    private fun checkCallExpr(o: RsCallExpr, holder: RsAnnotationHolder) {
        val resolved = (o.expr as? RsPathExpr)?.path?.reference?.resolve()
        if (resolved is RsFunction && resolved.isMethod) {
            val ty = o.inference?.getResolvedPath(o.expr as RsPathExpr)?.singleOrNull()?.subst?.get(TyTypeParameter.self())
            if (ty is TyTraitObject) {
                annotateDynCall(o, holder)
            }
        }
    }

    private fun AnnotationHolder.createDynCallAnnotation(textRange: TextRange, message: String) {
        if (isBatchMode) return
        val color = RsColor.DYN_CALL
        val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION

        newAnnotation(severity, message)
            .range(textRange)
            .textAttributes(color.textAttributesKey).create()
    }
}

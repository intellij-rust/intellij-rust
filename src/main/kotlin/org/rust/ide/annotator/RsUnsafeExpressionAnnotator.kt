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
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsUnsafeExpressionAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val rsHolder = RsAnnotationHolder(holder)
        val visitor = object : RsVisitor() {
            override fun visitCallExpr(o: RsCallExpr) = checkCall(o, rsHolder)
            override fun visitMethodCall(o: RsMethodCall) = checkMethodCall(o, rsHolder)
            override fun visitPathExpr(o: RsPathExpr) = checkPathExpr(o, rsHolder)
            override fun visitUnaryExpr(o: RsUnaryExpr) = checkUnary(o, rsHolder)
        }

        element.accept(visitor)
    }

    private fun annotateUnsafeCall(expr: RsExpr, holder: RsAnnotationHolder) {
        if (!expr.isEnabledByCfg) return

        if (expr.isInUnsafeBlockOrFn(/* skip the expression itself*/ 1)) {
            val textRange = when (expr) {
                is RsCallExpr -> when (val callee = expr.expr) {
                    is RsPathExpr -> callee.path.textRangeOfLastSegment
                    else -> callee.textRange
                }
                is RsDotExpr -> when (val call = expr.methodCall) {
                    null -> return // unreachable
                    else -> call.textRangeWithoutValueArguments
                }
                else -> return // unreachable
            }
            holder.holder.createUnsafeAnnotation(textRange, "Call to unsafe function")
        } else {
            RsDiagnostic.UnsafeError(expr, "Call to unsafe function requires unsafe function or block").addToHolder(holder)
        }
    }

    private fun annotateUnsafeStaticRef(expr: RsPathExpr, element: RsConstant, holder: RsAnnotationHolder) {
        val constantType = when {
            element.kind == RsConstantKind.MUT_STATIC -> "mutable"
            element.kind == RsConstantKind.STATIC && element.parent is RsForeignModItem -> "extern"
            else -> return
        }

        if (expr.isInUnsafeBlockOrFn()) {
            holder.holder.createUnsafeAnnotation(expr.path.textRangeOfLastSegment, "Use of unsafe $constantType static")
        } else {
            RsDiagnostic.UnsafeError(expr, "Use of $constantType static is unsafe and requires unsafe function or block")
                .addToHolder(holder)
        }
    }

    fun checkMethodCall(o: RsMethodCall, holder: RsAnnotationHolder) {
        val fn = o.reference.resolve() as? RsFunction ?: return

        if (fn.isActuallyUnsafe) {
            annotateUnsafeCall(o.parentDotExpr, holder)
        }
    }

    fun checkCall(element: RsCallExpr, holder: RsAnnotationHolder) {
        val path = (element.expr as? RsPathExpr)?.path ?: return
        val fn = path.reference?.resolve() as? RsFunction ?: return

        if (fn.isActuallyUnsafe) {
            annotateUnsafeCall(element, holder)
        }
    }

    fun checkPathExpr(expr: RsPathExpr, holder: RsAnnotationHolder) {
        val constant = expr.path.reference?.resolve() as? RsConstant ?: return
        annotateUnsafeStaticRef(expr, constant, holder)
    }

    fun checkUnary(element: RsUnaryExpr, holder: RsAnnotationHolder) {
        val mul = element.mul ?: return // operatorType != UnaryOperator.DEREF
        if (element.expr?.type !is TyPointer) return

        if (element.isInUnsafeBlockOrFn()) {
            holder.holder.createUnsafeAnnotation(mul.textRange, "Unsafe dereference of raw pointer")
        } else {
            RsDiagnostic.UnsafeError(element, "Dereference of raw pointer requires unsafe function or block")
                .addToHolder(holder)
        }
    }

    private fun PsiElement.isInUnsafeBlockOrFn(ancestorsToSkip: Int = 0): Boolean {
        val parent = this.ancestors
            .drop(ancestorsToSkip)
            .find {
                when (it) {
                    is RsBlockExpr -> it.isUnsafe
                    is RsFunction -> true
                    else -> false
                }
            } ?: return false

        return parent is RsBlockExpr || (parent is RsFunction && parent.isActuallyUnsafe)
    }

    private fun AnnotationHolder.createUnsafeAnnotation(textRange: TextRange, message: String) {
        val color = RsColor.UNSAFE_CODE
        val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION
        // BACKCOMPAT: 2019.3
        @Suppress("DEPRECATION")
        createAnnotation(severity, textRange, message).textAttributes = color.textAttributesKey
    }
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsUnsafeExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RsVisitor() {
            override fun visitCallExpr(o: RsCallExpr) = checkCall(o, holder)
            override fun visitMethodCall(o: RsMethodCall) = checkMethodCall(o, holder)
            override fun visitPath(o: RsPath) = checkPath(o, holder)
            override fun visitUnaryExpr(o: RsUnaryExpr) = checkUnary(o, holder)
        }

        element.accept(visitor)
    }

    private fun annotateUnsafeCall(o: RsExpr, holder: AnnotationHolder) {
        if (!o.isInUnsafeBlockOrFn(/* skip the expression itself*/ 1)) {
            RsDiagnostic.UnsafeError(o, "Call to unsafe function requires unsafe function or block").addToHolder(holder)
        } else {
            val refElement = if (o is RsCallExpr) o.expr else o
            holder.createUnsafeAnnotation(refElement, "Call to unsafe function")
        }
    }

    private fun annotateUnsafeStaticRef(expr: RsExpr, element: RsConstant, holder: AnnotationHolder) {
        val constantType = when {
            element.kind == RsConstantKind.MUT_STATIC -> "mutable"
            element.kind == RsConstantKind.STATIC && element.parent is RsForeignModItem -> "extern"
            else -> return
        }

        if (!expr.isInUnsafeBlockOrFn()) {
            RsDiagnostic.UnsafeError(expr, "Use of $constantType static is unsafe and requires unsafe function or block")
                .addToHolder(holder)
        } else {
            holder.createUnsafeAnnotation(expr, "Use of unsafe $constantType static")
        }
    }

    fun checkMethodCall(o: RsMethodCall, holder: AnnotationHolder) {
        val fn = o.reference.resolve() as? RsFunction ?: return

        if (fn.isUnsafe) {
            annotateUnsafeCall(o.parentDotExpr, holder)
        }
    }

    fun checkCall(element: RsCallExpr, holder: AnnotationHolder) {
        val path = (element.expr as? RsPathExpr)?.path ?: return
        val fn = path.reference.resolve() as? RsFunction ?: return

        if (fn.isUnsafe) {
            annotateUnsafeCall(element, holder)
        }
    }

    fun checkPath(o: RsPath, holder: AnnotationHolder) {
        val constant = o.reference.resolve() as? RsConstant ?: return
        val expr = o.ancestorOrSelf<RsExpr>() ?: return

        annotateUnsafeStaticRef(expr, constant, holder)
    }

    fun checkUnary(element: RsUnaryExpr, holder: AnnotationHolder) {
        if (element.operatorType != UnaryOperator.DEREF) return
        if (element.expr?.type !is TyPointer) return

        if (!element.isInUnsafeBlockOrFn()) {
            RsDiagnostic.UnsafeError(element, "Dereference of raw pointer requires unsafe function or block").addToHolder(holder)
        } else {
            holder.createUnsafeAnnotation(element, "Unsafe dereference of raw pointer")
        }
    }

    private fun PsiElement.isInUnsafeBlockOrFn(parentsToSkip: Int = 0): Boolean {
        val parent = this.ancestors
            .drop(parentsToSkip)
            .find {
                when (it) {
                    is RsBlockExpr -> it.isUnsafe
                    is RsFunction -> true
                    else -> false
                }
            } ?: return false

        return parent is RsBlockExpr || (parent is RsFunction && parent.isUnsafe)
    }
}

fun AnnotationHolder.createUnsafeAnnotation(element: PsiElement, message: String) {
    createWeakWarningAnnotation(element, message).textAttributes = RsColor.UNSAFE_CODE.textAttributesKey
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.openapiext.isUnitTestMode

class RsUnsafeExpressionAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val rsHolder = RsAnnotationHolder(holder)
        val visitor = object : RsVisitor() {
            override fun visitCallExpr(o: RsCallExpr) = checkCall(o, rsHolder)
            override fun visitDotExpr(o: RsDotExpr) = checkDotExpr(o, rsHolder)
            override fun visitPathExpr(o: RsPathExpr) = checkPathExpr(o, rsHolder)
            override fun visitUnaryExpr(o: RsUnaryExpr) = checkUnary(o, rsHolder)
            override fun visitMacroExpr(o: RsMacroExpr) = checkMacroExpr(o, rsHolder)
        }

        element.accept(visitor)
    }

    private fun annotateUnsafeCall(expr: RsExpr, holder: RsAnnotationHolder) {
        if (!expr.existsAfterExpansion) return

        if (expr.isInUnsafeContext) {
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
            holder.holder.createUnsafeAnnotation(textRange, RsBundle.message("inspection.message.call.to.unsafe.function"))
        } else {
            RsDiagnostic.UnsafeError(expr, RsBundle.message("inspection.message.call.to.unsafe.function.requires.unsafe.function.or.block")).addToHolder(holder)
        }
    }

    private fun annotateUnsafeStaticRef(expr: RsPathExpr, element: RsConstant, holder: RsAnnotationHolder) {
        val constantType = when {
            element.kind == RsConstantKind.MUT_STATIC -> RsBundle.message("inspection.message.mutable")
            element.kind == RsConstantKind.STATIC && element.parent is RsForeignModItem -> RsBundle.message("inspection.message.extern")
            else -> return
        }

        if (expr.isInUnsafeContext) {
            val textRange = expr.path.textRangeOfLastSegment ?: return
            holder.holder.createUnsafeAnnotation(textRange, RsBundle.message("inspection.message.use.unsafe.static", constantType))
        } else {
            RsDiagnostic.UnsafeError(expr, RsBundle.message("inspection.message.use.static.unsafe.requires.unsafe.function.or.block", constantType))
                .addToHolder(holder)
        }
    }

    fun checkDotExpr(o: RsDotExpr, holder: RsAnnotationHolder) {
        o.methodCall?.let {
            val fn = it.reference.resolve() as? RsFunction ?: return

            if (fn.isActuallyUnsafe) {
                annotateUnsafeCall(o, holder)
            }
        }

        val exprParent = o.parent
        if (exprParent is RsBinaryExpr && exprParent.operatorType == AssignmentOp.EQ && exprParent.left == o) return
        if (o.fieldLookup != null) {
            val type = o.expr.type
            if (type !is TyAdt) return
            val item = type.item
            if (item !is RsStructItem) return
            if (item.kind == RsStructKind.UNION && !o.expr.isInUnsafeContext) {
                RsDiagnostic.UnsafeError(o, RsBundle.message("inspection.message.access.to.union.field.unsafe.requires.unsafe.function.or.block")).addToHolder(holder)
            }
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

        if (element.isInUnsafeContext) {
            holder.holder.createUnsafeAnnotation(mul.textRange, RsBundle.message("inspection.message.unsafe.dereference.raw.pointer"))
        } else {
            RsDiagnostic.UnsafeError(element, RsBundle.message("inspection.message.dereference.raw.pointer.requires.unsafe.function.or.block"))
                .addToHolder(holder)
        }
    }

    fun checkMacroExpr(macroExpr: RsMacroExpr, holder: RsAnnotationHolder) {
        val macroCall = macroExpr.macroCall
        val macroName = macroCall.macroName

        if (UNSAFE_MACRO_LIST.contains(macroName)) {
            val macroDef = macroCall.resolveToMacro()

            if (macroDef != null && macroDef.hasRustcBuiltinMacro && !macroExpr.isInUnsafeContext) {
                RsDiagnostic.UnsafeError(
                    macroExpr,
                    RsBundle.message("inspection.message.use.unsafe.requires.unsafe.function.or.block", macroName)
                ).addToHolder(holder)
            }
        }
    }

    private fun AnnotationHolder.createUnsafeAnnotation(textRange: TextRange, @InspectionMessage message: String) {
        if (isBatchMode) return
        val color = RsColor.UNSAFE_CODE
        val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION

        newAnnotation(severity, message)
            .range(textRange)
            .textAttributes(color.textAttributesKey).create()
    }

    companion object {
        private val UNSAFE_MACRO_LIST = setOf("asm")
    }
}

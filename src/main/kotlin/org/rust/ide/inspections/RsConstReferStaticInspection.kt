/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.util.forEachDescendantOfType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.isConst
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsConstReferStaticInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitConstant(constant: RsConstant) {
            // We only want to target `const` constants (thus, excluding `static`) with this inspection.
            if (!constant.isConst) return

            errorOnStaticRef(constant.expr ?: return, holder) { expr ->
                RsDiagnostic.ConstVariableReferToStaticError(expr, constant, expr.text)
            }
        }

        override fun visitArrayType(arrayType: RsArrayType) {
            errorOnStaticRef(arrayType.expr ?: return, holder) { expr ->
                RsDiagnostic.ConstArraySizeReferToStaticError(expr, expr.text)
            }
        }

        override fun visitArrayExpr(arrayExpr: RsArrayExpr) {
            val lastExpr = arrayExpr.exprList.lastOrNull()
            if (arrayExpr.semicolon != null && lastExpr != null) {
                errorOnStaticRef(lastExpr, holder) { expr ->
                    RsDiagnostic.ConstArraySizeReferToStaticError(expr, expr.text)
                }
            }
        }

        override fun visitEnumVariant(enumVariant: RsEnumVariant) {
            val name = enumVariant.name ?: return
            errorOnStaticRef(enumVariant.variantDiscriminant?.expr ?: return, holder) { expr ->
                RsDiagnostic.ConstEnumDiscriminantReferToStaticError(expr, name, expr.text)
            }
        }

        override fun visitCallExpr(call: RsCallExpr) {
            val typeArgumentList = (call.expr as? RsPathExpr ?: return).path.typeArgumentList ?: return
            val constParams = ((call.expr as RsPathExpr).path.reference?.resolve() as RsFunction).typeParameterList?.constParameterList
            typeArgumentList.exprList.forEach {
                // TODO: Find out which const param this expression targets, and skip if it is not const.
                errorOnStaticRef(it, holder) { expr ->
                    RsDiagnostic.ConstTypeParameterReferToStaticError(expr, "placeholder", expr.text)
                }
            }
        }
    }

    private fun errorOnStaticRef(expr: RsExpr, holder: RsProblemsHolder, problemFactory: (RsPathExpr) -> RsDiagnostic) {
        expr.forEachDescendantOfType<RsPathExpr> { pathExpr ->
            // If the resolved reference is a constant,
            val ref = pathExpr.path.reference?.resolve() as? RsConstant ?: return@forEachDescendantOfType
            // and it is declared `static`,
            if (!ref.isConst) {
                // register the error.
                problemFactory(pathExpr).addToHolder(holder)
            }
        }
    }

}

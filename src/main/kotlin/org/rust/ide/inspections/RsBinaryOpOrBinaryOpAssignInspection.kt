/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.OverloadableBinaryOperator
import org.rust.lang.core.psi.ext.isAssignBinaryExpr
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsBinaryOpOrBinaryOpAssignInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? = object : RsVisitor() {
        override fun visitBinaryExpr(o: RsBinaryExpr) {
            val lhsTy = o.left.type
            val rhsTy = o.right?.type ?: return

            if (lhsTy != rhsTy) return

            val overloadableBinaryOperator = o.binaryOp.operatorType as? OverloadableBinaryOperator ?: return

            when (lhsTy) {
                is TyAdt -> {
                    val traitImpl = ImplLookup.relativeTo(o).findOverloadedOpImpl(lhsTy, rhsTy, overloadableBinaryOperator)

                    if (traitImpl != null) return

                    RsDiagnostic.UnsupportedBinaryOpOrOpAssign(
                        o,
                        o.isAssignBinaryExpr,
                        overloadableBinaryOperator,
                        lhsTy.toString()
                    ).addToHolder(holder)
                }
            }
        }
    }
}

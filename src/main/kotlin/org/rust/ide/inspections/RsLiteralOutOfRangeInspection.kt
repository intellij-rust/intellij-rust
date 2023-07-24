/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.fixes.ConvertTypeReferenceFix
import org.rust.ide.presentation.render
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.lang.utils.evaluation.validValuesRange

class RsLiteralOutOfRangeInspection: RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitLitExpr(expr: RsLitExpr) {
                val expectedTy = expr.type
                if (expectedTy !is TyInteger
                    || expectedTy is TyInteger.U128
                    || expectedTy is TyInteger.I128
                    || expectedTy is TyInteger.I32
                    || expectedTy is TyInteger.U64
                    || expectedTy is TyInteger.I64
                    || expectedTy is TyInteger.USize) return // Not supported, (should we even support them?)

                val literal = expr.kind as? RsLiteralKind.Integer ?: return

                val isNegative = (expr.context as? RsUnaryExpr)?.minus != null
                val numericValue = literal.value?.let { value -> if (isNegative) -value else value  } ?: return
                if (numericValue !in expectedTy.validValuesRange) {
                    val fix = findQuickFix(expr, expectedTy, numericValue)
                    RsDiagnostic.LiteralOutOfRange(expr, numericValue.toString(), expectedTy.render(), fix).addToHolder(holder)
                }
            }

            private fun findQuickFix(expr: RsLitExpr, currentType: TyInteger, overflownValue: Long): LocalQuickFix? {
                val proposedTy = findTypeUpgrade(currentType, overflownValue) ?: return null
                when (val parent = expr.context) {
                    is RsLetDecl -> {
                        val pat = (parent.pat as? RsPatIdent) ?: return null
                        val typeReference = parent.typeReference ?: return null
                        return ConvertTypeReferenceFix(typeReference, pat.patBinding.identifier.text, proposedTy)
                    }
                    is RsStructLiteralField -> {
                        val fieldDec = parent.reference.resolve() as? RsNamedFieldDecl ?: return null
                        val typeReference = fieldDec.typeReference ?: return null
                        if (fieldDec.containingCrate.origin != PackageOrigin.WORKSPACE) return null
                        return ConvertTypeReferenceFix(typeReference, fieldDec.identifier.text, proposedTy)
                    }
                }
                return null
            }

            private fun findTypeUpgrade(currentType: TyInteger, overflownValue: Long): TyInteger? {
                val i = TyInteger.VALUES.indexOf(currentType)
                return TyInteger.VALUES.drop(i).firstOrNull { overflownValue in it.validValuesRange }
            }
        }
}

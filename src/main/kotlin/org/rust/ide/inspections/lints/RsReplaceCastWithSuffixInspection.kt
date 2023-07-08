/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.ReplaceCastWithLiteralSuffixFix
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger

class RsReplaceCastWithSuffixInspection : RsLintInspection() {

    override fun getLint(element: PsiElement): RsLint = RsLint.UnnecessaryCast

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitCastExpr(castExpr: RsCastExpr) {
            val expr = castExpr.expr

            val typeReference = castExpr.typeReference
            val typeReferenceType = typeReference.rawType
            if (typeReferenceType !is TyInteger && typeReferenceType !is TyFloat) {
                return
            }
            if (typeReferenceType.aliasedBy != null) {
                return
            }

            if (!isValidSuffix(expr, typeReference.text)) {
                return
            }

            holder.registerLintProblem(
                castExpr,
                RsBundle.message("inspection.message.can.be.replaced.with.literal.suffix"),
                RsLintHighlightingType.WEAK_WARNING,
                fixes = listOf(ReplaceCastWithLiteralSuffixFix(castExpr))
            )
        }
    }

    private fun isValidSuffix(expr: RsExpr, suffix: String): Boolean {
        val kind = when (expr) {
            is RsLitExpr -> expr
            // -1 is an unary expression
            is RsUnaryExpr -> {
                if (expr.minus == null) return false
                val lit = expr.expr
                if (lit is RsLitExpr) lit else return false
            }

            else -> return false
        }.kind
        if (kind !is RsLiteralWithSuffix) return false

        return kind.suffix == null && isValidSuffix(kind, suffix)
    }

    private fun isValidSuffix(kind: RsLiteralWithSuffix, suffix: String): Boolean {
        // Special case for `1f32`, which is allowed even though f32 is not a valid integer suffix
        if (kind is RsLiteralKind.Integer && TyFloat.NAMES.contains(suffix)
            // But `0b11f32` is not allowed
            && !startsWithRadixPrefix(kind)) return true

        return kind.validSuffixes.contains(suffix)
    }

    private fun startsWithRadixPrefix(kind: RsLiteralKind.Integer): Boolean {
        return radixPrefixes.any { kind.node.text.startsWith(it) }
    }

    private val radixPrefixes = listOf("0x", "0b", "0o")
}

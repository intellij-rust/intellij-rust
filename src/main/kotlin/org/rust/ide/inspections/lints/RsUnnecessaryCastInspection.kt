/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.RemoveCastFix
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

class RsUnnecessaryCastInspection : RsLintInspection() {

    override fun getLint(element: PsiElement): RsLint = RsLint.UnnecessaryCast

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitCastExpr(castExpr: RsCastExpr) {
            val typeReferenceType = castExpr.typeReference.rawType
            val exprType = castExpr.expr.type

            /**
             * Consider the following code
             *
             * ```rust
             * use core::ffi::c_ulong;
             *
             * fn main() {
             *     let x: c_ulong = 0;
             *     let y = x as u64;
             * }
             * ```
             *
             * Casting to u64 seems like an unnecessary cast if `c_ulong` is `u64`
             * But it might actually be different types on different platforms
             * See https://github.com/rust-lang/rust-clippy/issues/10555 for more information
             *
             * Type alias might also always the same on all platforms, for example
             *
             * ```rust
             * // somewhere in a central place (library)
             * pub type AudioFloat = f32;
             *
             * // somewhere in client code:
             * let audio_buffer = vec![0.0 as AudioFloat; buffer_size];
             * ```
             *
             * In this case, `AudioFloat` is `f32` on all platforms,
             * and it might seem to be ok to use `0.0f32` instead of `0.0 as AudioFloat`.
             *
             * However, type of `AudioFloat` might change at any time during development
             * (for example, as an experiment, it might be changed to f64 to see how this affect computation accuracy and speed)
             * In this case, `0.0 as AudioFloat` is still correct, while `0.0f32` no longer is.
             * See also https://github.com/rust-lang/rust-clippy/issues/8018
             */
            if (isAlias(typeReferenceType)) return
            if (isAlias(exprType)) return

            if (containsUnknown(typeReferenceType)) return
            if (containsUnknown(exprType)) return

            /**
             * Consider the following code
             *
             * ```rust
             * fn accepts_i66(i: i64) {}
             *
             * fn main() {
             *     let a = 1 as i32;
             *     accepts_i64(a);
             * }
             * ```
             *
             * It doesn't compile because of the type mismatch
             * Removing `as i32` cast will make `a` be `i64` type due to type inference and make the code compile
             * Since this cast cannot be removed without changing the compilation result, it's not highlighted
             */
            if (isUnsuffixedNumber(castExpr.expr)) return

            if (exprType.isEquivalentTo(typeReferenceType)) {
                val castAs = castExpr.`as` ?: return
                holder.registerLintProblem(
                    castExpr,
                    RsBundle.message("inspection.message.unnecessary.cast"),
                    TextRange(castAs.textRange.startOffset - castExpr.textRange.startOffset, castExpr.typeReference.textRange.endOffset - castExpr.textRange.startOffset),
                    RsLintHighlightingType.UNUSED_SYMBOL,
                    fixes = listOf(RemoveCastFix(castExpr))
                )
            }
        }
    }

    private fun isAlias(ty: Ty): Boolean {
        val visitor = object : TypeVisitor {
            override fun visitTy(ty: Ty): Boolean {
                return ty.aliasedBy != null || ty.superVisitWith(this)
            }
        }
        return ty.visitWith(visitor)
    }

    private fun isUnsuffixedNumber(expr: RsExpr): Boolean {
        if (expr !is RsLitExpr) return false
        val kind = expr.kind
        if (kind !is RsLiteralWithSuffix) return false
        return kind.suffix == null
    }

    private fun containsUnknown(ty: Ty): Boolean {
        return ty.containsTyOfClass(TyUnknown::class.java)
    }
}

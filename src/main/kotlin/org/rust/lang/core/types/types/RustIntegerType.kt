package org.rust.lang.core.types.types

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsArrayExpr
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsVariantDiscriminant
import org.rust.lang.core.psi.util.sizeExpr

data class RustIntegerType(val kind: Kind) : RustPrimitiveType {

    companion object {
        fun fromLiteral(literal: PsiElement): RustIntegerType {
            val kind = Kind.values().find { literal.text.endsWith(it.name) }
                ?: inferKind(literal)

            return RustIntegerType(kind)
        }

        /**
         * Tries to infer the kind of an unsuffixed integer literal by its context.
         * Fall back to the default kind if can't infer.
         */
        private fun inferKind(literal: PsiElement): Kind {
            val expr = literal.parent as? RsLitExpr ?: return DEFAULT_KIND
            return when {
                expr.isArraySize -> Kind.usize
                expr.isEnumVariantDiscriminant -> Kind.isize
                else -> DEFAULT_KIND
            }
        }

        private val RsLitExpr.isArraySize: Boolean get() = (parent as? RsArrayExpr)?.sizeExpr == this
        private val RsLitExpr.isEnumVariantDiscriminant: Boolean get() = parent is RsVariantDiscriminant

        val DEFAULT_KIND = Kind.i32
    }

    enum class Kind {
        u8, u16, u32, u64, u128, usize,
        i8, i16, i32, i64, i128, isize
    }

    override fun toString(): String = kind.toString()

}

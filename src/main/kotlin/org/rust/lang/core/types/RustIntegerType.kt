package org.rust.lang.core.types

import com.intellij.psi.PsiElement

data class RustIntegerType(val kind: Kind) : RustPrimitiveType {

    companion object {
        fun fromLiteral(literal: PsiElement): RustIntegerType {
            val kind = Kind.values().find { literal.text.endsWith(it.name) }
                //FIXME: If an integer type can be uniquely determined from the surrounding program context,
                // the unsuffixed integer literal has that type. We use just i32 for now
                ?: Kind.i32

            return RustIntegerType(kind)
        }

    }

    enum class Kind {
        u8, u16, u32, u64, u128, usize,
        i8, i16, i32, i64, i128, isize
    }

    override fun toString(): String = kind.toString()

}

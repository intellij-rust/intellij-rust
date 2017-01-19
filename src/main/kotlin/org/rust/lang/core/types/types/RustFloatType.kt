package org.rust.lang.core.types.types

import com.intellij.psi.PsiElement

data class RustFloatType(val kind: Kind) : RustPrimitiveType {

    companion object {
        fun fromLiteral(literal: PsiElement): RustFloatType {
            val kind = Kind.values().find { literal.text.endsWith(it.name) }
                //FIXME: If an floating point type can be uniquely determined from the surrounding program context,
                // the unsuffixed floating point literal has that type. We use f64 for now.
                ?: Kind.f64

            return RustFloatType(kind)
        }
    }

    enum class Kind { f32, f64 }

    override fun toString(): String = kind.toString()

}

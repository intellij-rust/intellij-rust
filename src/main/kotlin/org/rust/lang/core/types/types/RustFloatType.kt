package org.rust.lang.core.types.types

import com.intellij.psi.PsiElement

data class RustFloatType(val kind: Kind, override val isKindWeak: Boolean = false) : RustNumericType {

    companion object {
        fun fromLiteral(literal: PsiElement): RustFloatType {
            val kind = Kind.values().find { literal.text.endsWith(it.name) }

            return RustFloatType(kind ?: DEFAULT_KIND, kind == null)
        }

        val DEFAULT_KIND = Kind.f64
    }

    enum class Kind { f32, f64 }

    override fun toString(): String = kind.toString()

}

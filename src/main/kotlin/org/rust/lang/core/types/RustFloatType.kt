package org.rust.lang.core.types

import com.intellij.psi.PsiElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustFloatType(val kind: Kind) : RustPrimitiveTypeBase() {

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

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitFloat(this)

    override fun toString(): String = kind.toString()

}

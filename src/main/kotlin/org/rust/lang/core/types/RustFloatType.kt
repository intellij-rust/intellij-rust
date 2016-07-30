package org.rust.lang.core.types

import org.rust.lang.core.psi.RustLitExprElement
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.unresolved.RustUnresolvedTypeBase
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

class RustFloatType(val kind: Kind) : RustUnresolvedTypeBase(), RustType {

    companion object {
        fun deduceBySuffix(text: String): RustFloatType? =
            Kind.values().find { it.name == text }?.let { RustFloatType(it) }

        //
        // TODO(xxx):
        //  The type of an unsuffixed integer literal is determined by type inference
        //      > If an integer type can be uniquely determined from the surrounding program context, the unsuffixed integer literal has that type.
        //      > If the program context under-constrains the type, it defaults to the 32-bit float
        //      > If the program context over-constrains the type, it is considered a static type error.
        //
        fun deduceUnsuffixed(o: RustLitExprElement): RustFloatType = RustFloatType(kind = RustFloatType.Kind.f32)
    }

    enum class Kind { f32, f64 }

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitFloat(this)

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitFloat(this)

//    override fun equals(other: Any?): Boolean = other is RustFloatType && other.kind === kind
//
//    override fun hashCode(): Int = kind.hashCode()

    override fun toString(): String = kind.toString()

}

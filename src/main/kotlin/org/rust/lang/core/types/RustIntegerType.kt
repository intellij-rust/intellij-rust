package org.rust.lang.core.types

import org.rust.lang.core.psi.RustLitExprElement
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.unresolved.RustUnresolvedTypeBase
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

class RustIntegerType(val kind: Kind): RustUnresolvedTypeBase(), RustType {

    companion object {
        fun deduceBySuffix(s: String): RustIntegerType? =
            Kind.values().find { s.endsWith(it.name) }?.let { RustIntegerType(it) }

        //
        // TODO(xxx):
        //  The type of an unsuffixed integer literal is determined by type inference
        //      > If an integer type can be uniquely determined from the surrounding program context, the unsuffixed integer literal has that type.
        //      > If the program context under-constrains the type, it defaults to the signed 32-bit integer i32.
        //      > If the program context over-constrains the type, it is considered a static type error.
        //
        fun deduceUnsuffixed(o: RustLitExprElement): RustIntegerType = RustIntegerType(kind = Kind.i32)
    }

    enum class Kind {
        u8, u16, u32, u64, usize,
        i8, i16, i32, i64, isize
    }

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitInteger(this)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitInteger(this)

    override fun toString(): String = kind.toString()

}

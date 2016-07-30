package org.rust.lang.core.types

import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.unresolved.RustUnresolvedTypeBase
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

object RustCharacterType : RustUnresolvedTypeBase(), RustType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitChar(this)

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitChar(this)

//    override fun equals(other: Any?): Boolean = other is RustCharacterType
//
//    override fun hashCode(): Int = 10099

    override fun toString(): String = "char"
}

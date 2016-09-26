package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

object RustStringType : RustPrimitiveTypeBase() {

    fun deduce(text: String): RustStringType? =
        if (text == "str") RustStringType else null

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitString(this)

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitString(this)

    override fun toString(): String = "str"
}

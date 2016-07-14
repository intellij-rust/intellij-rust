package org.rust.lang.core.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustStructType(struct: RustStructItemElement) : RustStructOrEnumTypeBase(struct) {
    val struct = CompletionUtil.getOriginalOrSelf(struct)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitStruct(this)

    override fun equals(other: Any?): Boolean = other is RustStructType && other.struct === struct

    override fun hashCode(): Int = struct.hashCode() * 10067 + 9631

    override fun toString(): String = struct.name ?: "<anonymous>"
}

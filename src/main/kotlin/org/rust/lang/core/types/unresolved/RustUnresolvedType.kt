package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustDeserializationUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustSerializationUnresolvedTypeVisitor
import java.io.DataInput
import java.io.DataOutput

interface RustUnresolvedType {

    fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String
}


fun DataInput.readRustUnresolvedType(): RustUnresolvedType =
    RustDeserializationUnresolvedTypeVisitor(this).visit()

fun DataOutput.writeRustUnresolvedType(value: RustUnresolvedType) =
    RustSerializationUnresolvedTypeVisitor(this).visit(value)

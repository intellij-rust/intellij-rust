package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustDeserializationUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustSerialisationUnresolvedTypeVisitor
import java.io.DataInput
import java.io.DataOutput

interface RustUnresolvedType {

    companion object {

        fun serialize(type: RustUnresolvedType?, output: DataOutput) {
            RustSerialisationUnresolvedTypeVisitor(output).visit(type)
        }

        fun deserialize(input: DataInput): RustUnresolvedType? =
            RustDeserializationUnresolvedTypeVisitor(input).visit()

    }

    fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

}


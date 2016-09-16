package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustDeserializationUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustSerialisationUnresolvedTypeVisitor
import org.rust.utils.RustDataExternalizer
import java.io.DataInput
import java.io.DataOutput

interface RustUnresolvedType {

    companion object : RustDataExternalizer<RustUnresolvedType> {
        override fun save(output: DataOutput, value: RustUnresolvedType) {
            RustSerialisationUnresolvedTypeVisitor(output).visit(value)
        }

        override fun read(input: DataInput): RustUnresolvedType =
            RustDeserializationUnresolvedTypeVisitor(input).visit()
    }

    fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

}


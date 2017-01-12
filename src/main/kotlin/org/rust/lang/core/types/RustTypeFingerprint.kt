package org.rust.lang.core.types

import org.rust.lang.core.psi.RustBaseTypeElement
import org.rust.lang.core.psi.RustRefLikeTypeElement
import org.rust.lang.core.psi.RustTypeElement
import java.io.DataInput
import java.io.DataOutput

data class RustTypeFingerprint private constructor(
    private val name: String
) {
    companion object {
        fun create(type: RustTypeElement): RustTypeFingerprint? = when (type) {
            is RustBaseTypeElement -> type.path?.referenceName?.let(::RustTypeFingerprint)
            is RustRefLikeTypeElement -> type.type?.let { create(it) }
            else -> null
        }

        fun create(type: RustType): RustTypeFingerprint? = when (type) {
            is RustStructType -> type.item.name?.let(::RustTypeFingerprint)
            is RustEnumType -> type.item.name?.let(::RustTypeFingerprint)
            is RustReferenceType -> create(type.referenced)
            else -> null
        }
    }

    object KeyDescriptor : com.intellij.util.io.KeyDescriptor<RustTypeFingerprint> {
        override fun save(out: DataOutput, value: RustTypeFingerprint) =
            out.writeUTF(value.name)

        override fun read(`in`: DataInput): RustTypeFingerprint =
            RustTypeFingerprint(`in`.readUTF())

        override fun getHashCode(value: RustTypeFingerprint): Int = value.hashCode()

        override fun isEqual(lhs: RustTypeFingerprint, rhs: RustTypeFingerprint): Boolean = lhs == rhs
    }
}

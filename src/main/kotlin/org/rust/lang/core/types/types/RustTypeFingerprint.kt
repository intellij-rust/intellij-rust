package org.rust.lang.core.types.types

import org.rust.lang.core.psi.RsArrayType
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.types.Ty
import java.io.DataInput
import java.io.DataOutput

data class RustTypeFingerprint constructor(
    private val name: String
) {
    companion object {
        fun create(type: RsTypeReference): RustTypeFingerprint? = when (type) {
            is RsBaseType -> type.path?.referenceName?.let(::RustTypeFingerprint)
            is RsRefLikeType -> type.typeReference?.let { create(it) }
            is RsArrayType -> RustTypeFingerprint("[T]")
            else -> null
        }

        fun create(type: Ty): RustTypeFingerprint? = when (type) {
            is RustStructType -> type.item.name?.let(::RustTypeFingerprint)
            is RustEnumType -> type.item.name?.let(::RustTypeFingerprint)
            is RustSliceType -> RustTypeFingerprint("[T]")
            is RustStringSliceType -> RustTypeFingerprint("str")
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

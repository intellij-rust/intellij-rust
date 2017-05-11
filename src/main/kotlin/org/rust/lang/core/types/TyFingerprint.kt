package org.rust.lang.core.types

import org.rust.lang.core.psi.RsArrayType
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.types.ty.*
import java.io.DataInput
import java.io.DataOutput

/**
 * A type fingerprint used for indexing. It should satisfy two properties:
 *
 *  * `ty1 == ty2 => fingerprint(ty1) == fingerprint(ty2)`.
 *  * fingerprint can be computed without name resolution.
 */
data class TyFingerprint constructor(
    private val name: String
) {
    companion object {
        fun create(type: RsTypeReference): TyFingerprint? = when (type) {
            is RsBaseType -> type.path?.referenceName?.let(::TyFingerprint)
            is RsRefLikeType -> type.typeReference?.let { create(it) }
            is RsArrayType -> TyFingerprint("[T]")
            else -> null
        }

        fun create(type: Ty): TyFingerprint? = when (type) {
            is TyStruct -> type.item.name?.let(::TyFingerprint)
            is TyEnum -> type.item.name?.let(::TyFingerprint)
            is TySlice -> TyFingerprint("[T]")
            is TyStr -> TyFingerprint("str")
            is TyReference -> create(type.referenced)
            else -> null
        }
    }

    object KeyDescriptor : com.intellij.util.io.KeyDescriptor<TyFingerprint> {
        override fun save(out: DataOutput, value: TyFingerprint) =
            out.writeUTF(value.name)

        override fun read(`in`: DataInput): TyFingerprint =
            TyFingerprint(`in`.readUTF())

        override fun getHashCode(value: TyFingerprint): Int = value.hashCode()

        override fun isEqual(lhs: TyFingerprint, rhs: TyFingerprint): Boolean = lhs == rhs
    }
}

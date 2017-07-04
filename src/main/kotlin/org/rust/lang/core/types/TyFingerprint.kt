/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.RsArrayType
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.isPointer
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
        fun create(ref: RsTypeReference): TyFingerprint? {
            val type = ref.typeElement
            return when (type) {
                is RsBaseType -> type.path?.referenceName?.let(::TyFingerprint)
                is RsRefLikeType -> {
                    if (type.isPointer)
                        TyFingerprint("*T")
                    else
                        type.typeReference?.let { create(it) }
                }
                is RsArrayType -> TyFingerprint("[T]")
                else -> null
            }
        }

        fun create(type: Ty): TyFingerprint? = when (type) {
            is TyStruct -> type.item.name?.let(::TyFingerprint)
            is TyEnum -> type.item.name?.let(::TyFingerprint)
            is TySlice -> TyFingerprint("[T]")
            is TyPointer -> TyFingerprint("*T")
            is TyReference -> create(type.referenced)
            is TyPrimitive -> TyFingerprint(type.toString())
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

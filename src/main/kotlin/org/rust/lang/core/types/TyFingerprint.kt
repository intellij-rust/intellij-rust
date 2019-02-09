/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsBaseTypeKind
import org.rust.lang.core.psi.ext.isPointer
import org.rust.lang.core.psi.ext.kind
import org.rust.lang.core.psi.ext.typeElement
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

        val TYPE_PARAMETER_FINGERPRINT = TyFingerprint("#T")
        private val ANY_INTEGER_FINGERPRINT = TyFingerprint("{integer}")
        private val ANY_FLOAT_FINGERPRINT = TyFingerprint("{float}")

        // Keep in sync with Declarations-inferTypeReferenceType
        fun create(ref: RsTypeReference, typeParameters: List<String>): List<TyFingerprint> {
            val type = ref.typeElement
            val fingerprint = when (type) {
                is RsTupleType -> TyFingerprint("(tuple)")
                is RsBaseType -> when (val kind = type.kind) {
                    RsBaseTypeKind.Unit -> TyFingerprint("()")
                    RsBaseTypeKind.Never -> TyFingerprint("!")
                    RsBaseTypeKind.Underscore -> return emptyList()
                    is RsBaseTypeKind.Path -> when (val name = kind.path.referenceName) {
                        in typeParameters -> TYPE_PARAMETER_FINGERPRINT
                        in TyInteger.NAMES -> return listOf(TyFingerprint(name), ANY_INTEGER_FINGERPRINT)
                        in TyFloat.NAMES -> return listOf(TyFingerprint(name), ANY_FLOAT_FINGERPRINT)
                        else -> TyFingerprint(name)
                    }
                }
                is RsRefLikeType -> {
                    if (type.isPointer) {
                        TyFingerprint("*T")
                    } else {
                        return create(type.typeReference, typeParameters)
                    }
                }
                is RsArrayType -> TyFingerprint("[T]")
                is RsFnPointerType -> TyFingerprint("fn()")
                else -> return emptyList()
            }
            return listOf(fingerprint)
        }

        fun create(type: Ty): TyFingerprint? = when (type) {
            is TyAdt -> type.item.name?.let(::TyFingerprint)
            is TySlice, is TyArray -> TyFingerprint("[T]")
            is TyPointer -> TyFingerprint("*T")
            is TyReference -> create(type.referenced)
            is TyTuple -> TyFingerprint("(tuple)")
            is TyPrimitive -> TyFingerprint(type.toString())
            is TyFunction -> TyFingerprint("fn()")
            is TyInfer.IntVar -> ANY_INTEGER_FINGERPRINT
            is TyInfer.FloatVar -> ANY_FLOAT_FINGERPRINT
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

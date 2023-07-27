/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.isImpl
import org.rust.lang.core.psi.ext.isPointer
import org.rust.lang.core.psi.ext.isSlice
import org.rust.lang.core.psi.ext.skipParens
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
    val name: String
) {
    companion object {

        val TYPE_PARAMETER_OR_MACRO_FINGERPRINT = TyFingerprint("#T")
        private val ANY_INTEGER_FINGERPRINT = TyFingerprint("{integer}")
        private val ANY_FLOAT_FINGERPRINT = TyFingerprint("{float}")

        // Keep in sync with Declarations-inferTypeReferenceType
        fun create(ref: RsTypeReference, typeParameters: List<String>): List<TyFingerprint> {
            val fingerprint = when (val type = ref.skipParens()) {
                is RsTupleType -> TyFingerprint("(tuple)")
                is RsUnitType -> TyFingerprint("()")
                is RsNeverType -> TyFingerprint("!")
                is RsInferType -> return emptyList()
                is RsPathType -> when (val name = type.path.referenceName) {
                    null -> return emptyList()
                    in typeParameters -> TYPE_PARAMETER_OR_MACRO_FINGERPRINT
                    in TyInteger.NAMES -> return listOf(TyFingerprint(name), ANY_INTEGER_FINGERPRINT)
                    in TyFloat.NAMES -> return listOf(TyFingerprint(name), ANY_FLOAT_FINGERPRINT)
                    else -> TyFingerprint(name)
                }
                is RsRefLikeType -> {
                    if (type.isPointer) {
                        TyFingerprint("*T")
                    } else {
                        return type.typeReference?.let { create(it, typeParameters) } ?: emptyList()
                    }
                }
                is RsArrayType -> if (type.isSlice) {
                    TyFingerprint("[T]")
                } else {
                    TyFingerprint("[T;]")
                }
                is RsFnPointerType -> TyFingerprint("fn()")
                is RsTraitType -> if (!type.isImpl) {
                    TyFingerprint("dyn T")
                } else {
                    return emptyList()
                }
                is RsMacroType -> TYPE_PARAMETER_OR_MACRO_FINGERPRINT
                else -> return emptyList()
            }
            return listOf(fingerprint)
        }

        fun create(type: Ty): TyFingerprint? = when (type) {
            is TyAdt -> type.item.name?.let(::TyFingerprint)
            is TySlice -> TyFingerprint("[T]")
            is TyArray -> TyFingerprint("[T;]")
            is TyPointer -> TyFingerprint("*T")
            is TyReference -> create(type.referenced)
            is TyTuple -> TyFingerprint("(tuple)")
            is TyPrimitive -> TyFingerprint(type.toString())
            is TyFunctionBase -> TyFingerprint("fn()")
            is TyTraitObject -> TyFingerprint("dyn T")
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

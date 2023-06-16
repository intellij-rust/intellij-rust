/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.rustFile
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.evaluation.ThreeValuedLogic
import org.rust.lang.utils.evaluation.or


// TODO Param, Placeholder, Infer, Generators, Closures
fun ImplLookup.needsDrop(ty: Ty, rsElement: RsElement): ThreeValuedLogic =
    NeedsDropCheck(
        this,
        rsElement.containingFile.rustFile?.getRecursionLimit(rsElement.containingCrate) ?: DEFAULT_RECURSION_LIMIT
    ).needsDrop(ty, 0)

private class NeedsDropCheck(
    private val implLookup: ImplLookup,
    private val recursionLimit: Int
) {

    private val needsDropCache = hashMapOf<Ty, ThreeValuedLogic>()

    fun needsDrop(ty: Ty, depth: Int): ThreeValuedLogic = needsDropCache.getOrPut(ty) { needsDropRaw(ty, depth) }

    private fun needsDropRaw(ty: Ty, depth: Int): ThreeValuedLogic {
        if (depth > recursionLimit) return ThreeValuedLogic.True // TODO overflow error

        return when {
            ty is TyUnknown -> ThreeValuedLogic.Unknown
            ty is TyAnon
                || ty is TyTraitObject -> ThreeValuedLogic.True

            ty is TyPrimitive
                || ty is TyReference
                || ty is TyPointer
                || ty is TyFunctionBase -> ThreeValuedLogic.False

            implLookup.isDrop(ty) == ThreeValuedLogic.True -> ThreeValuedLogic.True
            implLookup.isCopy(ty) == ThreeValuedLogic.True -> ThreeValuedLogic.False
            ty is TyAdt -> checkAdt(ty, depth)
            ty is TyTuple -> checkTuple(ty, depth)
            ty is TyArray -> needsDrop(ty.base, depth + 1)
            ty is TySlice -> needsDrop(ty.elementType, depth + 1)
            else -> ThreeValuedLogic.Unknown
        }
    }

    private fun checkAdt(ty: TyAdt, depth: Int): ThreeValuedLogic {
        val item = ty.item
        if (item == implLookup.items.ManuallyDrop) return ThreeValuedLogic.False

        return when (item) {
            is RsStructItem -> {
                if (item.kind == RsStructKind.UNION) return ThreeValuedLogic.False
                checkAdtFields(item, ty.typeParameterValues, depth)
            }

            is RsEnumItem -> {
                checkEnum(item, ty.typeParameterValues, depth)
            }

            else -> {
                ThreeValuedLogic.Unknown
            }
        }
    }

    private fun checkTuple(ty: TyTuple, depth: Int): ThreeValuedLogic {
        var result = ThreeValuedLogic.False
        for (type in ty.types) {
            result = result or needsDrop(type, depth + 1)
            if (result == ThreeValuedLogic.True) return ThreeValuedLogic.True
        }

        return result
    }

    private fun checkEnum(enum: RsEnumItem, substitution: Substitution, depth: Int): ThreeValuedLogic {
        var result = ThreeValuedLogic.False
        for (variant in enum.enumBody?.enumVariantList ?: return ThreeValuedLogic.False) {
            result = result or checkAdtFields(variant, substitution, depth)
            if (result == ThreeValuedLogic.True) return ThreeValuedLogic.True
        }

        return result
    }

    private fun checkAdtFields(fieldsOwner: RsFieldsOwner, substitution: Substitution, depth: Int): ThreeValuedLogic {
        var result = ThreeValuedLogic.False
        val fields = (fieldsOwner.tupleFields?.tupleFieldDeclList ?: emptyList()) +
            (fieldsOwner.blockFields?.namedFieldDeclList ?: emptyList())

        for (field in fields) {
            val typeReference = field.typeReference
            result = result or if (typeReference != null) {
                needsDrop(typeReference.rawType.substitute(substitution), depth + 1)
            } else {
                ThreeValuedLogic.Unknown
            }

            if (result == ThreeValuedLogic.True) return ThreeValuedLogic.True
        }

        return result
    }
}

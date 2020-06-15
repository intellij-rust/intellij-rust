/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.openapiext.Testmark
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByValue
import org.rust.lang.core.resolve.ref.resolvePath
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.type
import org.rust.lang.utils.evaluation.ConstExpr
import org.rust.lang.utils.evaluation.toConst

fun RsPat.extractBindings(fcx: RsTypeInferenceWalker, type: Ty, defBm: RsBindingModeKind = BindByValue(IMMUTABLE)) {
    when (this) {
        is RsPatWild -> fcx.writePatTy(this, type)
        is RsPatConst -> {
            val expr = expr
            val expected = when {
                expr is RsLitExpr && expr.kind is RsLiteralKind.String -> type
                expr is RsPathExpr && resolvePath(expr.path).singleOrNull()?.element is RsConstant -> type
                else -> type.stripReferences(defBm).first
            }
            fcx.writePatTy(this, expected)
            fcx.inferTypeCoercableTo(expr, expected)
        }
        is RsPatRef -> {
            pat.extractBindings(fcx, (type as? TyReference)?.referenced ?: TyUnknown)
            fcx.writePatTy(this, type)
        }
        is RsPatRange -> {
            val (expected, _) = type.stripReferences(defBm)
            fcx.writePatTy(this, expected)
            patConstList.forEach { fcx.inferTypeCoercableTo(it.expr, expected) }
        }
        is RsPatIdent -> {
            val patBinding = patBinding
            val resolved = patBinding.reference.resolve()
            val bindingType = if (resolved is RsEnumVariant) {
                type.stripReferences(defBm).first
            } else {
                patBinding.inferType(type, defBm)
            }
            fcx.writePatTy(this, bindingType)
            pat?.extractBindings(fcx, type)
        }
        is RsPatTup -> {
            val (expected, bm) = type.stripReferences(defBm)
            fcx.writePatTy(this, expected)
            val types = (expected as? TyTuple)?.types.orEmpty()
            inferTupleFieldsTypes(fcx, patList, bm, types.size) { types.getOrElse(it) { TyUnknown } }
        }
        is RsPatTupleStruct -> {
            val (expected, bm) = type.stripReferences(defBm)
            fcx.writePatTy(this, expected)
            val item = path.reference?.resolve() as? RsFieldsOwner
                ?: ((expected as? TyAdt)?.item as? RsStructItem)
                ?: return

            val tupleFields = item.positionalFields
            inferTupleFieldsTypes(fcx, patList, bm, tupleFields.size) { idx ->
                tupleFields
                    .getOrNull(idx)
                    ?.typeReference
                    ?.type
                    ?.substituteOrUnknown(expected.typeParameterValues)
                    ?: TyUnknown
            }
        }
        is RsPatStruct -> {
            val (expected, mut) = type.stripReferences(defBm)
            fcx.writePatTy(this, expected)
            val item = path.reference?.resolve() as? RsFieldsOwner
                ?: ((expected as? TyAdt)?.item as? RsStructItem)
                ?: return

            val structFields = item.fields.associateBy { it.name }
            for (patField in patFieldList) {
                val kind = patField.kind
                val fieldType = structFields[kind.fieldName]
                    ?.typeReference
                    ?.type
                    ?.substituteOrUnknown(expected.typeParameterValues)
                    ?: TyUnknown

                when (kind) {
                    is RsPatFieldKind.Full -> {
                        kind.pat.extractBindings(fcx, fieldType, mut)
                        fcx.writePatFieldTy(patField, fieldType)
                    }
                    is RsPatFieldKind.Shorthand -> {
                        val bindingType = if (fieldType is TyAdt && kind.isBox) {
                            fieldType.typeArguments.singleOrNull() ?: return
                        } else {
                            fieldType
                        }
                        fcx.writePatFieldTy(patField, kind.binding.inferType(bindingType, mut))
                    }
                }
            }
        }
        is RsPatSlice -> {
            val (expected, bm) = type.stripReferences(defBm)
            fcx.writePatTy(this, expected)
            inferSlicePatsTypes(fcx, patList, bm, expected)
        }
        is RsPatBox -> {
            val (expected, bm) = type.stripReferences(defBm)
            fcx.writePatTy(this, expected)
            if (expected is TyAdt && expected.isBox) {
                val boxed = expected.typeArguments.singleOrNull() ?: return
                pat.extractBindings(fcx, boxed, bm)
            }
        }
        is RsOrPat -> {
            for (pat in patList) {
                pat.extractBindings(fcx, type, defBm)
            }
            fcx.writePatTy(this, type)
        }
        else -> {
            // not yet handled
        }
    }
}

private val RsPat.isRest: Boolean
    get() = this is RsPatRest || this is RsPatIdent && pat is RsPatRest

private fun inferSlicePatsTypes(
    fcx: RsTypeInferenceWalker,
    patList: List<RsPat>,
    bm: RsBindingModeKind,
    sliceType: Ty
) {
    fun calcRestSize(arrayTy: TyArray): Const {
        val arraySize = arrayTy.size ?: return CtUnknown

        val patRestCount = patList.count { it.isRest }
        if (patRestCount != 1) {
            PatternMatchingTestMarks.multipleRestPats.hit()
            return CtUnknown
        }

        val restSize = arraySize - patList.size.toLong() + 1
        if (restSize < 0) {
            PatternMatchingTestMarks.negativeRestSize.hit()
            return CtUnknown
        }

        return ConstExpr.Value.Integer(restSize, TyInteger.USize).toConst()
    }

    val (elementType, restType) = when (sliceType) {
        is TyArray -> sliceType.base to sliceType.copy(const = calcRestSize(sliceType))
        is TySlice -> sliceType.elementType to sliceType
        else -> TyUnknown to TyUnknown
    }

    for (pat in patList) {
        val patType = if (pat.isRest) restType else elementType
        pat.extractBindings(fcx, patType, bm)
    }
}

private fun inferTupleFieldsTypes(
    fcx: RsTypeInferenceWalker,
    patList: List<RsPat>,
    bm: RsBindingModeKind,
    tupleSize: Int,
    type: (Int) -> Ty
) {
    // In correct code, tuple or tuple struct patterns contain only one `..` pattern.
    // But it's pretty simple to support type inference for cases with multiple `..` patterns like `let (x, .., y, .., z) = tuple`
    // just ignoring all binding between first and last `..` patterns
    var firstPatRestIndex = Int.MAX_VALUE
    var lastPatRestIndex = -1
    for ((index, pat) in patList.withIndex()) {
        if (pat.isRest) {
            firstPatRestIndex = minOf(firstPatRestIndex, index)
            lastPatRestIndex = maxOf(lastPatRestIndex, index)
        }
    }

    for ((idx, p) in patList.withIndex()) {
        val fieldType = when {
            idx < firstPatRestIndex -> type(idx)
            idx > lastPatRestIndex -> type(tupleSize - (patList.size - idx))
            else -> TyUnknown
        }
        p.extractBindings(fcx, fieldType, bm)
    }
}

private fun RsPatBinding.inferType(expected: Ty, defBm: RsBindingModeKind): Ty {
    val bm = run {
        val bm = kind
        if (bm is BindByValue && bm.mutability == IMMUTABLE) {
            defBm
        } else {
            bm
        }
    }
    return if (bm is BindByReference) TyReference(expected, bm.mutability) else expected
}

private fun Ty.stripReferences(defBm: RsBindingModeKind): Pair<Ty, RsBindingModeKind> {
    var bm = defBm
    var ty = this
    while (ty is TyReference) {
        bm = when (bm) {
            is BindByValue -> BindByReference(ty.mutability)
            is BindByReference -> BindByReference(
                if (bm.mutability == IMMUTABLE) IMMUTABLE else ty.mutability
            )
        }
        ty = ty.referenced
    }
    return ty to bm
}

object PatternMatchingTestMarks {
    val multipleRestPats = Testmark("multipleRestPats")
    val negativeRestSize = Testmark("negativeRestSize")
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByValue
import org.rust.lang.core.resolve.ref.resolvePath
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.type

fun RsPat.extractBindings(fcx: RsFnInferenceContext, type: Ty, defBm: RsBindingModeKind = BindByValue(IMMUTABLE)) {
    when (this) {
        is RsPatWild -> {}
        is RsPatConst -> {
            val expr = expr
            val expectedTy = when {
                expr is RsLitExpr && expr.kind is RsLiteralKind.String -> type
                expr is RsPathExpr && resolvePath(expr.path).singleOrNull()?.element is RsConstant-> type
                else -> type.stripReferences(defBm).first
            }
            fcx.inferTypeCoercableTo(expr, expectedTy)
        }
        is RsPatRef -> {
            pat.extractBindings(fcx, (type as? TyReference)?.referenced ?: TyUnknown)
        }
        is RsPatRange -> {
            val (derefTy, _) = type.stripReferences(defBm)
            patConstList.forEach { fcx.inferTypeCoercableTo(it.expr, derefTy) }
        }
        is RsPatIdent -> {
            val patBinding = patBinding
            val bindingType = patBinding.inferType(type, defBm)
            fcx.writeBindingTy(patBinding, bindingType)
            pat?.extractBindings(fcx, type)
        }
        is RsPatTup -> {
            val (derefTy, mb) = type.stripReferences(defBm)
            val types = (derefTy as? TyTuple)?.types.orEmpty()
            for ((idx, p) in patList.withIndex()) {
                p.extractBindings(fcx, types.getOrElse(idx) { TyUnknown }, mb)
            }
        }
        is RsPatTupleStruct -> {
            val (derefTy, bm) = type.stripReferences(defBm)
            val item = path.reference.resolve() as? RsFieldsOwner
                ?: ((derefTy as? TyAdt)?.item as? RsStructItem)
                ?: return

            val tupleFields = item.positionalFields
            for ((idx, p) in patList.withIndex()) {
                val fieldType = tupleFields
                    .getOrNull(idx)
                    ?.typeReference
                    ?.type
                    ?.substituteOrUnknown(derefTy.typeParameterValues)
                    ?: TyUnknown
                p.extractBindings(fcx, fieldType, bm)
            }
        }
        is RsPatStruct -> {
            val (derefTy, mut) = type.stripReferences(defBm)
            val item = path.reference.resolve() as? RsFieldsOwner
                ?: ((derefTy as? TyAdt)?.item as? RsStructItem)
                ?: return

            val structFields = item.fields.associateBy { it.name }
            for (patField in patFieldList) {
                val kind = patField.kind
                val fieldType = structFields[kind.fieldName]
                    ?.typeReference
                    ?.type
                    ?.substituteOrUnknown(derefTy.typeParameterValues)
                    ?: TyUnknown

                when (kind) {
                    is RsPatFieldKind.Full -> kind.pat.extractBindings(fcx, fieldType, mut)
                    is RsPatFieldKind.Shorthand -> {
                        val bindingType = if (fieldType is TyAdt && kind.isBox) {
                            fieldType.typeArguments.singleOrNull() ?: return
                        }  else {
                            fieldType
                        }
                        fcx.writeBindingTy(kind.binding, kind.binding.inferType(bindingType, mut))
                    }
                }
            }
        }
        is RsPatSlice -> {
            val (derefTy, bm) = type.stripReferences(defBm)
            val elementType = when (derefTy) {
                is TyArray -> derefTy.base
                is TySlice -> derefTy.elementType
                else -> TyUnknown
            }
            patList.forEach { it.extractBindings(fcx, elementType, bm) }
        }
        is RsPatBox -> {
            val (derefTy, bm) = type.stripReferences(defBm)
            if (derefTy is TyAdt && derefTy.isBox) {
                val boxed = derefTy.typeArguments.singleOrNull() ?: return
                pat.extractBindings(fcx, boxed, bm)
            }
        }
        else -> {
            // not yet handled
        }
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

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

fun RsPat.extractBindings(fcx: RsFnInferenceContext, type: Ty, ignoreRef: Boolean = false) {
    when (this) {
        is RsPatWild -> {}
        is RsPatConst -> {
            val expr = expr
            val expectedTy = when {
                expr is RsLitExpr && expr.kind is RsLiteralKind.String -> type
                else -> type.stripReferences().first
            }
            fcx.inferTypeCoercableTo(expr, expectedTy)
        }
        is RsPatRef -> {
            pat.extractBindings(fcx, (type as? TyReference)?.referenced ?: TyUnknown)
        }
        is RsPatRange -> {
            val (derefTy, _) = type.stripReferences()
            patConstList.forEach { fcx.inferTypeCoercableTo(it.expr, derefTy) }
        }
        is RsPatIdent -> {
            val patBinding = patBinding
            val kind = patBinding.kind
            val bindingType = if (kind is BindByReference && !ignoreRef) TyReference(type, kind.mutability) else type
            fcx.writeBindingTy(patBinding, bindingType)
            pat?.extractBindings(fcx, type)
        }
        is RsPatTup -> {
            val (derefTy, mut) = type.stripReferences()
            val types = (derefTy as? TyTuple)?.types.orEmpty()
            for ((idx, p) in patList.withIndex()) {
                p.extractBindings(fcx, types.getOrElse(idx) { TyUnknown }.toRefIfNeeded(mut), mut != null)
            }
        }
        is RsPatTupleStruct -> {
            val (derefTy, mut) = type.stripReferences()
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
                p.extractBindings(fcx, fieldType.toRefIfNeeded(mut), mut != null)
            }
        }
        is RsPatStruct -> {
            val (derefTy, mut) = type.stripReferences()
            val item = path.reference.resolve() as? RsFieldsOwner
                ?: ((derefTy as? TyAdt)?.item as? RsStructItem)
                ?: return

            val structFields = item.namedFields.associateBy { it.name }
            for (patField in patFieldList) {
                val kind = patField.kind
                val fieldType = structFields[kind.fieldName]
                    ?.typeReference
                    ?.type
                    ?.substituteOrUnknown(derefTy.typeParameterValues)
                    ?: TyUnknown

                when (kind) {
                    is RsPatFieldKind.Full -> kind.pat.extractBindings(fcx, fieldType.toRefIfNeeded(mut), mut != null)
                    is RsPatFieldKind.Shorthand -> {
                        val bindingKind = kind.binding.kind
                        val bindingType = fieldType.toRefIfNeeded(if (bindingKind is BindByReference) bindingKind.mutability else mut)
                        fcx.writeBindingTy(kind.binding, bindingType)
                    }
                }
            }
        }
        is RsPatSlice -> {
            val (derefTy, mut) = type.stripReferences()
            val elementType = when (derefTy) {
                is TyArray -> derefTy.base
                is TySlice -> derefTy.elementType
                else -> TyUnknown
            }
            patList.forEach { it.extractBindings(fcx, elementType.toRefIfNeeded(mut), mut != null) }
        }
        else -> {
            // not yet handled
        }
    }
}

private fun Ty.stripReferences(): Pair<Ty, Mutability?> {
    var isMut = true
    var ty = this
    while (ty is TyReference) {
        isMut = isMut && ty.mutability.isMut
        ty = ty.referenced
    }
    return ty to (if (this is TyReference) Mutability.valueOf(isMut) else null)
}

private fun Ty.toRefIfNeeded(mut: Mutability?): Ty =
    mut?.let { TyReference(this, mut) } ?: this

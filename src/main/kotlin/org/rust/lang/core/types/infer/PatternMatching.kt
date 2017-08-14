/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.isRef
import org.rust.lang.core.psi.ext.mutability
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

fun collectBindings(pattern: RsPat, type: Ty): Map<RsPatBinding, Ty> {
    val bindings: MutableMap<RsPatBinding, Ty> = mutableMapOf()

    // Here, we try to infer as much types as possible, so we use
    // type only as a hint
    fun go(pat: RsPat, type: Ty) {
        when (pat) {
            is RsPatIdent -> {
                val patBinding = pat.patBinding
                val bindingType = if (patBinding.isRef) TyReference(type, patBinding.mutability) else type
                bindings += patBinding to bindingType
                pat.pat?.let { go(it, type) }
            }
            is RsPatTup -> {
                val types = (type as? TyTuple)?.types.orEmpty()
                for ((idx, p) in pat.patList.withIndex()) {
                    go(p, types.getOrElse(idx, { TyUnknown }))
                }
            }
            is RsPatEnum -> {
                // the type might actually be either a tuple variant of enum, or a tuple struct.
                val ref = pat.path.reference.resolve()
                val tupleFields = (ref as? RsFieldsOwner)?.tupleFields
                    ?: (type as? TyStruct)?.item?.tupleFields
                    ?: return

                for ((idx, p) in pat.patList.withIndex()) {
                    val fieldType = tupleFields.tupleFieldDeclList
                        .getOrNull(idx)
                        ?.typeReference
                        ?.type
                        ?.substitute(type.typeParameterValues)
                        ?: TyUnknown
                    go(p, fieldType)
                }
            }
            is RsPatStruct -> {
                val struct = pat.path.reference.resolve() as? RsFieldsOwner
                    ?: (type as? TyStruct)?.item
                    ?: return

                val structFields = struct.blockFields?.fieldDeclList?.associateBy { it.name }.orEmpty()
                for (patField in pat.patFieldList) {
                    val fieldPun = patField.patBinding
                    val fieldName = if (fieldPun != null) {
                        // Foo { bar }
                        fieldPun.identifier.text
                    } else {
                        patField.identifier?.text
                            ?: error("`pat_field` may be either `pat_binding` or should contain identifier! ${patField.text}")
                    }
                    val fieldType = structFields[fieldName]
                        ?.typeReference
                        ?.type
                        ?.substitute(type.typeParameterValues)
                        ?: TyUnknown
                    patField.pat?.let { go(it, fieldType) }
                    if (fieldPun != null) {
                        bindings[fieldPun] = fieldType
                    }
                }
            }
            is RsPatRef -> go(pat.pat, (type as? TyReference)?.referenced ?: TyUnknown)
            else -> {
                // not yet handled
            }
        }
    }

    go(pattern, type)
    return bindings
}


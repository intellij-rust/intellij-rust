package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.types.Ty
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.RustReferenceType
import org.rust.lang.core.types.types.RustStructType
import org.rust.lang.core.types.types.RustTupleType
import org.rust.lang.core.types.types.TyUnknown

fun inferPatternBindingType(binding: RsPatBinding, pattern: RsPat, patternType: Ty): Ty {
    val bindings = collectBindings(pattern, patternType)
    return bindings[binding] ?: TyUnknown
}

private fun collectBindings(pattern: RsPat, type: Ty): Map<RsPatBinding, Ty> {
    val bindings: MutableMap<RsPatBinding, Ty> = mutableMapOf()

    // Here, we try to infer as much types as possible, so we use
    // type only as a hint
    fun go(pat: RsPat, type: Ty) {
        when (pat) {
            is RsPatIdent -> {
                bindings += pat.patBinding to type
                pat.pat?.let { go(it, type) }
            }
            is RsPatTup -> {
                val types = (type as? RustTupleType)?.types.orEmpty()
                for ((idx, p) in pat.patList.withIndex()) {
                    go(p, types.getOrElse(idx, { TyUnknown }))
                }
            }
            is RsPatEnum -> {
                // the type might actually be either a tuple variant of enum, or a tuple struct.
                val ref = pat.path.reference.resolve()
                val tupleFields = (ref as? RsFieldsOwner)?.tupleFields
                    ?: (type as? RustStructType)?.item?.tupleFields
                    ?: return

                for ((idx, p) in pat.patList.withIndex()) {
                    go(p, tupleFields.tupleFieldDeclList.getOrNull(idx)?.typeReference?.type ?: TyUnknown)
                }
            }
            is RsPatStruct -> {
                val struct = pat.path.reference.resolve() as? RsFieldsOwner
                    ?: (type as? RustStructType)?.item
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
                    val fieldType = structFields[fieldName]?.typeReference?.type ?: TyUnknown
                    patField.pat?.let { go(it, fieldType) }
                    if (fieldPun != null) {
                        bindings[fieldPun] = fieldType
                    }
                }
            }
            is RsPatRef -> go(pat.pat, (type as? RustReferenceType)?.referenced ?: TyUnknown)
            else -> {
                // not yet handled
            }
        }
    }

    go(pattern, type)
    return bindings
}

